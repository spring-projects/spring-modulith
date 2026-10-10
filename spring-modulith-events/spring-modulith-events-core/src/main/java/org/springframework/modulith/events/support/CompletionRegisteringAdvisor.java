/*
 * Copyright 2023-2026 the original author or authors.
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.modulith.events.core.EventListenerMethodMetadata;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.Assert;

/**
 * An {@link org.springframework.aop.Advisor} to decorate {@link TransactionalEventListener} annotated methods to mark
 * the previously registered event publications as completed on successful method execution.
 *
 * @author Oliver Drotbohm
 */
public class CompletionRegisteringAdvisor extends AbstractCompletionRegisteringAdvisor {

	private static final long serialVersionUID = 5649563426118669238L;

	/**
	 * Creates a new {@link CompletionRegisteringAdvisor} for the given {@link EventPublicationRegistry}, decorating all
	 * {@link TransactionalEventListener} annotated methods, no matter whether they are selected via
	 * {@code spring.modulith.events.registry-trigger-annotation}.
	 *
	 * @param registry must not be {@literal null}.
	 * @deprecated since 2.2, 2.1.2, for removal in 2.3. Use
	 *             {@link #CompletionRegisteringAdvisor(Supplier, EventListenerMethodMetadata)} instead to honor the
	 *             configured registry trigger annotation.
	 */
	@Deprecated(since = "2.2, 2.1.2", forRemoval = true)
	public CompletionRegisteringAdvisor(Supplier<EventPublicationRegistry> registry) {
		this(registry, EventListenerMethodMetadata.all());
	}

	/**
	 * Creates a new {@link CompletionRegisteringAdvisor} for the given {@link EventPublicationRegistry}, only decorating
	 * methods that actually trigger an entry in the former according to the given {@link EventListenerMethodMetadata}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @since 2.2, 2.1.2
	 */
	public CompletionRegisteringAdvisor(Supplier<EventPublicationRegistry> registry,
			EventListenerMethodMetadata metadata) {
		this(registry, metadata, TransactionOperations::withoutTransaction);
	}

	/**
	 * Creates a new {@link CompletionRegisteringAdvisor} for the given {@link EventPublicationRegistry}, only decorating
	 * methods that actually trigger an entry in the former according to the given {@link EventListenerMethodMetadata}.
	 * The given {@link TransactionOperations} are used to complete publications in a new transaction if completion does
	 * not take place inside the listener's transaction.
	 *
	 * @param registry must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @param transactions must not be {@literal null}, expected to start a new transaction.
	 * @since 2.2
	 */
	public CompletionRegisteringAdvisor(Supplier<EventPublicationRegistry> registry,
			EventListenerMethodMetadata metadata, Supplier<? extends TransactionOperations> transactions) {
		super(metadata, new CompletionRegisteringMethodInterceptor(registry, transactions));
	}

	/**
	 * {@link MethodInterceptor} to trigger the completion of an event publication after a transaction event listener
	 * method has been completed successfully.
	 *
	 * @author Oliver Drotbohm
	 */
	static class CompletionRegisteringMethodInterceptor implements MethodInterceptor, Ordered {

		private static final Logger LOG = LoggerFactory.getLogger(CompletionRegisteringMethodInterceptor.class);

		private final @NonNull Supplier<EventPublicationRegistry> registry;
		private final Supplier<? extends TransactionOperations> transactions;

		/**
		 * Creates a new {@link CompletionRegisteringMethodInterceptor} for the given {@link EventPublicationRegistry}.
		 *
		 * @param registry must not be {@literal null}.
		 */
		CompletionRegisteringMethodInterceptor(Supplier<EventPublicationRegistry> registry,
				Supplier<? extends TransactionOperations> transactions) {

			Assert.notNull(registry, "EventPublicationRegistry must not be null!");
			Assert.notNull(transactions, "TransactionOperations must not be null!");

			this.registry = registry;
			this.transactions = transactions;
		}

		/*
		 * (non-Javadoc)
		 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
		 */
		@Override
		public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {

			Object result = null;
			var method = invocation.getMethod();
			var argument = invocation.getArguments()[0];

			registerStateTransition(method, argument, EventPublicationRegistry::markProcessing);

			try {

				result = invocation.proceed();

				// Completion already taken care of in the listener's transaction
				if (isCompletionHandled(invocation)) {
					return result;
				}

				if (result instanceof CompletableFuture<?> future) {

					return future
							.thenApply(it -> {
								completeInNewTransaction(method, argument);
								return it;
							})
							.exceptionallyCompose(it -> {
								handleFailure(method, argument, it);
								return CompletableFuture.failedFuture(it);
							});
				}

			} catch (Throwable o_O) {

				handleFailure(method, argument, o_O);

				throw o_O;
			}

			completeInNewTransaction(method, argument);

			return result;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.Ordered#getOrder()
		 */
		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE + 10;
		}

		private void completeInNewTransaction(Method method, Object event) {
			transactions.get()
					.executeWithoutResult(__ -> registerStateTransition(method, event, EventPublicationRegistry::markCompleted));
		}

		private void handleFailure(Method method, Object event, Throwable o_O) {

			registerStateTransition(method, event, EventPublicationRegistry::markFailed);

			if (LOG.isDebugEnabled()) {
				LOG.debug("Invocation of listener {} failed. Leaving event publication uncompleted.", method, o_O);
			} else {
				LOG.info("Invocation of listener {} failed with message {}. Leaving event publication uncompleted.",
						method, o_O.getMessage());
			}
		}

		private void registerStateTransition(Method method, Object event,
				RegistryInvoker invoker) {

			invoker.invoke(registry.get(), event, ListenerIds.get(method));
		}

		private interface RegistryInvoker {
			void invoke(EventPublicationRegistry registry, Object event, PublicationTargetIdentifier identifier);
		}
	}
}
