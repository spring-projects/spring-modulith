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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.modulith.events.core.EventListenerMethodMetadata;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/**
 * An {@link org.springframework.aop.Advisor} to be registered <em>after</em> the transaction interceptor to complete
 * event publications right before the listener's transaction commits.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 * @see TransactionalCompletionRegisteringInterceptor
 */
class InTransactionCompletionRegisteringAdvisor extends AbstractCompletionRegisteringAdvisor {

	private static final long serialVersionUID = 6150497308581467127L;

	/**
	 * Creates a new {@link InTransactionCompletionRegisteringAdvisor}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @param async whether the listener invocation is executed asynchronously, which allows to wait for
	 *          {@link java.util.concurrent.CompletableFuture} results inside the transaction.
	 */
	InTransactionCompletionRegisteringAdvisor(Supplier<EventPublicationRegistry> registry,
			EventListenerMethodMetadata metadata, boolean async) {

		super(metadata, new InTransactionCompletionRegisteringInterceptor(registry, async));
	}

	/**
	 * {@link MethodInterceptor} registered <em>after</em> the transaction interceptor in the advisor chain of a
	 * transactional event listener. It registers a {@link TransactionSynchronization} that marks the event publication as
	 * completed right before the listener's transaction commits, so that completion is atomic with the listener's work.
	 * <p>
	 * If the listener is executed asynchronously and returns a {@link CompletableFuture}, the interceptor waits for the
	 * future to complete inside the transaction (the framework would do so right after the commit anyway) and only
	 * registers the synchronization on success, so that a failed future fails the transaction. A synchronously executed
	 * listener is never waited for. The synchronization is registered unless it returns a future that is still pending or
	 * failed. If no transaction is active, or no synchronization has been registered, completion is left to the
	 * {@link CompletionRegisteringAdvisor.CompletionRegisteringMethodInterceptor}.
	 *
	 * @author Oliver Drotbohm
	 * @since 2.2
	 */
	static class InTransactionCompletionRegisteringInterceptor implements MethodInterceptor {

		private final Supplier<EventPublicationRegistry> registry;
		private final boolean async;

		InTransactionCompletionRegisteringInterceptor(Supplier<EventPublicationRegistry> registry, boolean async) {

			Assert.notNull(registry, "EventPublicationRegistry must not be null!");

			this.registry = registry;
			this.async = async;
		}

		/*
		 * (non-Javadoc)
		 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
		 */
		@Override
		public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {

			var method = invocation.getMethod();

			if (!(invocation instanceof ProxyMethodInvocation proxyInvocation)
					|| !TransactionSynchronizationManager.isSynchronizationActive()
					|| TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
				return invocation.proceed();
			}

			var result = invocation.proceed();

			if (result instanceof CompletableFuture<?> future) {

				if (async) {

					// The framework would wait for the result right after the transaction has committed.
					// Waiting here makes the outcome part of the transaction.
					await(future);

				} else if (!future.isDone() || future.isCompletedExceptionally()) {

					// Synchronous invocations must not be waited for. Leave pending or failed results to the
					// outer interceptor, which completes or fails the publication as soon as the result is known.
					return result;
				}
			}

			var event = invocation.getArguments()[0];
			var identifier = ListenerIds.get(method);

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void beforeCommit(boolean readOnly) {
					registry.get().markCompleted(event, identifier);
				}
			});

			markCompletionHandled(proxyInvocation);

			return result;
		}

		private static void await(CompletableFuture<?> future) throws Throwable {

			try {
				future.get();
			} catch (ExecutionException o_O) {
				throw o_O.getCause() == null ? o_O : o_O.getCause();
			} catch (InterruptedException o_O) {

				Thread.currentThread().interrupt();

				throw o_O;
			}
		}
	}
}
