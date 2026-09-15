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
package org.springframework.modulith.events.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * First-class collection to work with transactional event listeners, i.e. {@link ApplicationListener} instances that
 * implement {@link TransactionalApplicationListener}.
 *
 * @author Oliver Drotbohm
 * @author Seonwoo Jung
 * @see org.springframework.transaction.event.TransactionalEventListener
 * @see TransactionalApplicationListener
 * @since 2.2, previously package private
 */
public class TransactionalEventListeners {

	static final String TRIGGER_ANNOTATION_PROPERTY = "spring.modulith.events.registry-trigger-annotation";

	private static final Method GET_TARGET_METHOD;

	static {

		GET_TARGET_METHOD = ReflectionUtils
				.findMethod(TransactionalApplicationListenerMethodAdapter.class, "getTargetMethod");
		ReflectionUtils.makeAccessible(GET_TARGET_METHOD);
	}

	private final List<TransactionalApplicationListener<ApplicationEvent>> listeners;

	/**
	 * Creates a new {@link TransactionalEventListeners} instance by filtering all elements implementing
	 * {@link TransactionalApplicationListener}.
	 *
	 * @param listeners must not be {@literal null}.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public TransactionalEventListeners(Collection<ApplicationListener<?>> listeners,
			Supplier<Environment> environment) {

		Assert.notNull(listeners, "ApplicationListeners must not be null!");

		this.listeners = (List) listeners.stream()
				.filter(TransactionalApplicationListener.class::isInstance)
				.map(TransactionalApplicationListener.class::cast)
				.filter(it -> it.getTransactionPhase().equals(TransactionPhase.AFTER_COMMIT))
				.filter(byAnnotationFilter(environment))
				.sorted(AnnotationAwareOrderComparator.INSTANCE)
				.toList();
	}

	private TransactionalEventListeners(List<TransactionalApplicationListener<ApplicationEvent>> listeners) {
		this.listeners = listeners;
	}

	/**
	 * Executes the given consumer only if there are actual listeners available.
	 *
	 * @param metadata must not be {@literal null}.
	 */
	public void ifPresent(Consumer<Stream<TransactionalApplicationListener<ApplicationEvent>>> metadata) {

		Assert.notNull(metadata, "Callback must not be null!");

		if (!listeners.isEmpty()) {
			metadata.accept(listeners.stream());
		}
	}

	public TransactionalEventListeners filter(
			Predicate<? super TransactionalApplicationListener<ApplicationEvent>> filter) {

		return listeners.stream().filter(filter)
				.collect(Collectors.collectingAndThen(Collectors.toUnmodifiableList(), TransactionalEventListeners::new));
	}

	/**
	 * Invokes the listener matching the target identifier of the given {@link TargetEventPublication}, or hands off to
	 * the given callback if no matching listener is currently registered.
	 *
	 * @param publication must not be {@literal null}.
	 * @param onMissing must not be {@literal null}.
	 * @since 2.2
	 */
	public void invoke(TargetEventPublication publication, Consumer<TargetEventPublication> onMissing) {

		Assert.notNull(publication, "TargetEventPublication must not be null!");
		Assert.notNull(onMissing, "Callback must not be null!");

		stream()
				.filter(it -> publication.isIdentifiedBy(PublicationTargetIdentifier.of(it.getListenerId())))
				.findFirst()
				.ifPresentOrElse(it -> it.processEvent(publication.getApplicationEvent()), () -> onMissing.accept(publication));
	}

	/**
	 * Returns all transactional event listeners.
	 *
	 * @return will never be {@literal null}.
	 */
	Stream<TransactionalApplicationListener<ApplicationEvent>> stream() {
		return listeners.stream();
	}

	/**
	 * Returns a {@link Predicate} filtering the listeners by the trigger annotation configured in
	 * {@code spring.modulith.events.annotation}.
	 *
	 * @param environment must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 2.1
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Predicate<TransactionalApplicationListener> byAnnotationFilter(
			Supplier<Environment> environment) {

		return listener -> {

			var annotationName = environment.get().getProperty(TRIGGER_ANNOTATION_PROPERTY);

			if (!StringUtils.hasText(annotationName)) {
				return true;
			}

			try {

				var annotationType = ClassUtils.forName(annotationName, TransactionalEventListeners.class.getClassLoader());

				if (!annotationType.isAnnotation()) {
					throw new IllegalStateException("Configured type is not an annotation!");
				}

				if (!(listener instanceof TransactionalApplicationListenerMethodAdapter)) {
					return false;
				}

				var method = (Method) ReflectionUtils.invokeMethod(GET_TARGET_METHOD, listener);

				return AnnotatedElementUtils.hasAnnotation(method, (Class<? extends Annotation>) annotationType);

			} catch (ClassNotFoundException o_O) {
				throw new IllegalStateException(o_O);
			}
		};
	}
}
