/*
 * Copyright 2017-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.events.EventPublicationRegistry;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;

/**
 * {@link BeanPostProcessor} that will add a
 * {@link CompletionRegisteringBeanPostProcessor.ProxyCreatingMethodCallback.CompletionRegisteringMethodInterceptor}
 * to the bean in case it carries a {@link TransactionalEventListener} annotation so that the successful invocation of
 * those methods mark the event publication to those listeners as completed.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
public class CompletionRegisteringBeanPostProcessor implements BeanPostProcessor {

	private final Supplier<EventPublicationRegistry> registry;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

		ProxyCreatingMethodCallback callback = new ProxyCreatingMethodCallback(registry, beanName, bean, false);

		ReflectionUtils.doWithMethods(AopProxyUtils.ultimateTargetClass(bean), callback);

		return callback.methodFound ? callback.getBean() : bean;

	}

	/**
	 * Method callback to find a {@link TransactionalEventListener} method and creating a proxy including an
	 * {@link CompletionRegisteringBeanPostProcessor} for it or adding the latter to the already existing advisor chain.
	 *
	 * @author Oliver Drotbohm
	 */
	@AllArgsConstructor
	private static class ProxyCreatingMethodCallback implements MethodCallback {

		private @NonNull final Supplier<EventPublicationRegistry> registry;
		private @NonNull final String beanName;
		private @NonNull @Getter Object bean;
		private boolean methodFound;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.util.ReflectionUtils.MethodCallback#doWith(java.lang.reflect.Method)
		 */
		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {

			if (methodFound || !CompletionRegisteringMethodInterceptor.isCompletingMethod(method)) {
				return;
			}

			this.methodFound = true;
			this.bean = createCompletionRegisteringProxy(bean,
					new CompletionRegisteringMethodInterceptor(registry, beanName));
		}

		private static Object createCompletionRegisteringProxy(Object bean, Advice interceptor) {

			if (bean instanceof Advised) {

				Advised advised = (Advised) bean;
				advised.addAdvice(advised.getAdvisors().length, interceptor);

				return bean;
			}

			ProxyFactory factory = new ProxyFactory(bean);
			factory.setProxyTargetClass(true);
			factory.addAdvice(interceptor);

			return factory.getProxy();
		}
	}

	/**
	 * {@link MethodInterceptor} to trigger the completion of an event publication after a transaction event listener
	 * method has been completed successfully.
	 *
	 * @author Oliver Drotbohm
	 */
	@Slf4j
	@RequiredArgsConstructor
	private static class CompletionRegisteringMethodInterceptor implements MethodInterceptor, Ordered {

		private static final ConcurrentLruCache<Method, Boolean> COMPLETING_METHOD = new ConcurrentLruCache<>(100,
				CompletionRegisteringMethodInterceptor::calculateIsCompletingMethod);
		private static final ConcurrentLruCache<CacheKey, TransactionalApplicationListenerMethodAdapter> ADAPTERS = new ConcurrentLruCache<>(
				100, CompletionRegisteringMethodInterceptor::createAdapter);

		private final @NonNull Supplier<EventPublicationRegistry> registry;
		private final @NonNull String beanName;

		/*
		 * (non-Javadoc)
		 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
		 */
		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {

			Object result = null;
			Method method = invocation.getMethod();

			try {
				result = invocation.proceed();
			} catch (Exception o_O) {

				if (!isCompletingMethod(method)) {
					throw o_O;
				}

				if (LOG.isDebugEnabled()) {
					LOG.debug("Invocation of listener {} failed. Leaving event publication uncompleted.", method, o_O);
				} else {
					LOG.info("Invocation of listener {} failed with message {}. Leaving event publication uncompleted.",
							method, o_O.getMessage());
				}

				return result;
			}

			if (!isCompletingMethod(method)) {
				return result;
			}

			// Mark publication complete if the method is a transactional event listener.
			String adapterId = ADAPTERS.get(CacheKey.of(beanName, method)).getListenerId();
			PublicationTargetIdentifier identifier = PublicationTargetIdentifier.of(adapterId);
			registry.get().markCompleted(invocation.getArguments()[0], identifier);

			return result;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.Ordered#getOrder()
		 */
		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE - 10;
		}

		/**
		 * Returns whether the given method is one that requires publication completion.
		 *
		 * @param method must not be {@literal null}.
		 * @return
		 */
		static boolean isCompletingMethod(Method method) {

			Assert.notNull(method, "Method must not be null!");

			return COMPLETING_METHOD.get(method);
		}

		private static boolean calculateIsCompletingMethod(Method method) {

			TransactionalEventListener annotation = AnnotatedElementUtils.getMergedAnnotation(method,
					TransactionalEventListener.class);

			return annotation == null ? false : annotation.phase().equals(TransactionPhase.AFTER_COMMIT);
		}

		private static TransactionalApplicationListenerMethodAdapter createAdapter(CacheKey key) {
			return new TransactionalApplicationListenerMethodAdapter(key.beanName, key.method.getDeclaringClass(),
					key.method);
		}
	}

	@Value(staticConstructor = "of")
	static class CacheKey {

		String beanName;
		Method method;
	}
}
