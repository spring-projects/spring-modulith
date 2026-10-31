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
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * Metadata about event listener methods, in particular which of them are to be considered by the Event Publication
 * Registry, as configured in the {@link Environment}. By default, all listeners (meta-)annotated with
 * {@link TransactionalEventListener} and listening for {@link TransactionPhase#AFTER_COMMIT} trigger an entry in
 * the registry.
 * Configuring an annotation via {@code spring.modulith.events.registry-trigger-annotation} narrows that down to the
 * listeners carrying it, which in turn causes all other listeners to be left alone by the registry entirely.
 *
 * @author Oliver Drotbohm
 * @since 2.2, 2.1.2
 */
public class EventListenerMethodMetadata {

	static final String TRIGGER_ANNOTATION_PROPERTY = "spring.modulith.events.registry-trigger-annotation";

	private static final Method GET_TARGET_METHOD;

	static {

		GET_TARGET_METHOD = ReflectionUtils
				.findMethod(TransactionalApplicationListenerMethodAdapter.class, "getTargetMethod");
		ReflectionUtils.makeAccessible(GET_TARGET_METHOD);
	}

	private final Supplier<@Nullable Class<? extends Annotation>> triggerAnnotation;

	private EventListenerMethodMetadata(Supplier<@Nullable Class<? extends Annotation>> triggerAnnotation) {
		this.triggerAnnotation = triggerAnnotation;
	}

	/**
	 * Creates a new {@link EventListenerMethodMetadata} for the given {@link Environment}. The latter is only accessed on
	 * first use and the trigger annotation resolved from it cached.
	 *
	 * @param environment must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static EventListenerMethodMetadata of(Supplier<Environment> environment) {

		Assert.notNull(environment, "Environment must not be null!");

		return new EventListenerMethodMetadata(
				SingletonSupplier.ofNullable(() -> findTriggerAnnotation(environment.get())));
	}

	/**
	 * Creates a new {@link EventListenerMethodMetadata} considering all transactional event listener methods, i.e. the
	 * arrangement in place if no trigger annotation is configured.
	 *
	 * @return will never be {@literal null}.
	 */
	public static EventListenerMethodMetadata all() {
		return new EventListenerMethodMetadata(() -> null);
	}

	/**
	 * Returns whether the given {@link Method} is supposed to trigger an entry in the Event Publication Registry, i.e.
	 * whether it is an {@link TransactionPhase#AFTER_COMMIT} {@link TransactionalEventListener} carrying the configured
	 * trigger annotation, if any.
	 *
	 * @param method must not be {@literal null}.
	 */
	public boolean triggersRegistry(Method method) {

		Assert.notNull(method, "Method must not be null!");

		var annotation = AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);

		return annotation != null
				&& annotation.phase().equals(TransactionPhase.AFTER_COMMIT)
				&& hasTriggerAnnotation(method);
	}

	/**
	 * Returns whether the given {@link TransactionalApplicationListener} is supposed to trigger an entry in the Event
	 * Publication Registry, i.e. whether it listens for {@link TransactionPhase#AFTER_COMMIT} and is backed by a method
	 * carrying the configured trigger annotation, if any. Listeners not backed by an event listener method are excluded
	 * as soon as a trigger annotation is configured, as their qualification cannot be established.
	 *
	 * @param listener must not be {@literal null}.
	 */
	boolean triggersRegistry(TransactionalApplicationListener<?> listener) {

		Assert.notNull(listener, "TransactionalApplicationListener must not be null!");

		if (!listener.getTransactionPhase().equals(TransactionPhase.AFTER_COMMIT)) {
			return false;
		}

		if (triggerAnnotation.get() == null) {
			return true;
		}

		if (!(listener instanceof TransactionalApplicationListenerMethodAdapter adapter)) {
			return false;
		}

		var method = (Method) ReflectionUtils.invokeMethod(GET_TARGET_METHOD, adapter);

		return method != null && hasTriggerAnnotation(method);
	}

	/**
	 * Returns whether the given {@link Method} carries the configured trigger annotation, if any.
	 *
	 * @param method must not be {@literal null}.
	 */
	private boolean hasTriggerAnnotation(Method method) {

		var annotationType = triggerAnnotation.get();

		return annotationType == null || AnnotatedElementUtils.hasAnnotation(method, annotationType);
	}

	/**
	 * Returns the annotation type configured via {@value #TRIGGER_ANNOTATION_PROPERTY} or {@literal null} if none is
	 * configured.
	 *
	 * @param environment must not be {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	private static @Nullable Class<? extends Annotation> findTriggerAnnotation(Environment environment) {

		var annotationName = environment.getProperty(TRIGGER_ANNOTATION_PROPERTY);

		if (!StringUtils.hasText(annotationName)) {
			return null;
		}

		try {

			var annotationType = ClassUtils.forName(annotationName, EventListenerMethodMetadata.class.getClassLoader());

			if (!annotationType.isAnnotation()) {
				throw new IllegalStateException("Configured type is not an annotation!");
			}

			return (Class<? extends Annotation>) annotationType;

		} catch (ClassNotFoundException o_O) {
			throw new IllegalStateException(o_O);
		}
	}
}
