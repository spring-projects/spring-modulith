/*
 * Copyright 2022-2023 the original author or authors.
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
package org.springframework.modulith.observability;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RootResourceInformation;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
public class SpringDataRestModuleTracingBeanPostProcessor extends ModuleTracingSupport implements BeanPostProcessor {

	private final ApplicationModulesRuntime runtime;
	private final Supplier<Tracer> tracer;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

		Class<?> type = runtime.getUserClass(bean, beanName);

		if (!AnnotatedElementUtils.hasAnnotation(type, BasePathAwareController.class)) {
			return bean;
		}

		Advice interceptor = new DataRestControllerInterceptor(runtime, tracer);
		Advisor advisor = new DefaultPointcutAdvisor(interceptor);

		return addAdvisor(bean, advisor, it -> it.setProxyTargetClass(true));
	}

	@RequiredArgsConstructor
	private static class DataRestControllerInterceptor implements MethodInterceptor {

		private final Supplier<ApplicationModules> modules;
		private final Supplier<Tracer> tracer;

		/*
		 * (non-Javadoc)
		 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
		 */
		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {

			ApplicationModule module = getModuleFrom(invocation.getArguments());

			if (module == null) {
				return invocation.proceed();
			}

			ObservedModule observed = new DefaultObservedModule(module);

			return ModuleEntryInterceptor.of(observed, tracer.get()).invoke(invocation);
		}

		private ApplicationModule getModuleFrom(Object[] arguments) {

			for (Object argument : arguments) {

				if (!RootResourceInformation.class.isInstance(arguments)) {
					continue;
				}

				RootResourceInformation info = (RootResourceInformation) argument;

				return modules.get().getModuleByType(info.getDomainType().getName()).orElse(null);
			}

			return null;
		}
	}

}
