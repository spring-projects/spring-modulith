/*
 * Copyright 2026 the original author or authors.
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

import java.lang.reflect.Method;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.modulith.events.core.EventListenerMethodMetadata;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.Assert;

/**
 * Base class for {@link org.springframework.aop.Advisor}s taking part in the completion of event publications. Makes
 * sure all of them apply to the same set of methods, i.e. {@link TransactionalEventListener} annotated ones that
 * actually trigger an entry in the registry according to the given {@link EventListenerMethodMetadata}.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
abstract class AbstractCompletionRegisteringAdvisor extends AbstractPointcutAdvisor {

	private static final long serialVersionUID = -2540230684713524211L;
	private static final String COMPLETION_HANDLED = AbstractCompletionRegisteringAdvisor.class.getName()
			+ ".completionHandled";

	private final Pointcut pointcut;
	private final Advice advice;

	/**
	 * Creates a new {@link AbstractCompletionRegisteringAdvisor} for the given {@link EventListenerMethodMetadata} and
	 * {@link Advice}.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param advice must not be {@literal null}.
	 */
	AbstractCompletionRegisteringAdvisor(EventListenerMethodMetadata metadata, Advice advice) {

		Assert.notNull(metadata, "EventListenerMethodMetadata must not be null!");
		Assert.notNull(advice, "Advice must not be null!");

		this.pointcut = new AnnotationMatchingPointcut(null, TransactionalEventListener.class, true) {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.aop.support.annotation.AnnotationMatchingPointcut#getMethodMatcher()
			 */
			@Override
			public MethodMatcher getMethodMatcher() {
				return new CommitListenerMethodMatcher(super.getMethodMatcher(), metadata);
			}
		};

		this.advice = advice;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.aop.PointcutAdvisor#getPointcut()
	 */
	@Override
	public Pointcut getPointcut() {
		return pointcut;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.aop.Advisor#getAdvice()
	 */
	@Override
	public Advice getAdvice() {
		return advice;
	}

	/**
	 * Returns whether the completion of the publication for the given invocation is taken care of by a transaction
	 * synchronization.
	 *
	 * @param invocation must not be {@literal null}.
	 */
	static boolean isCompletionHandled(MethodInvocation invocation) {

		return invocation instanceof ProxyMethodInvocation proxyInvocation
				&& proxyInvocation.getUserAttribute(COMPLETION_HANDLED) != null;
	}

	/**
	 * Marks the completion of the publication for the given invocation as taken care of by a transaction
	 * synchronization.
	 *
	 * @param invocation must not be {@literal null}.
	 * @see #isCompletionHandled(MethodInvocation)
	 */
	static void markCompletionHandled(ProxyMethodInvocation invocation) {
		invocation.setUserAttribute(COMPLETION_HANDLED, Boolean.TRUE);
	}

	/**
	 * An adapter for a delegating {@link MethodMatcher} to additionally verify that the method triggers the registry.
	 *
	 * @author Oliver Drotbohm
	 */
	private static class CommitListenerMethodMatcher extends StaticMethodMatcher {

		private final MethodMatcher delegate;
		private final EventListenerMethodMetadata metadata;

		CommitListenerMethodMatcher(MethodMatcher delegate, EventListenerMethodMetadata metadata) {

			this.delegate = delegate;
			this.metadata = metadata;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.aop.MethodMatcher#matches(java.lang.reflect.Method, java.lang.Class)
		 */
		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return delegate.matches(method, targetClass) && metadata.triggersRegistry(method);
		}
	}
}
