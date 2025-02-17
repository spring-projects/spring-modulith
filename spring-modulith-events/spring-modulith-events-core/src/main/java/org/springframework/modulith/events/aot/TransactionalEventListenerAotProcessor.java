/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.modulith.events.aot;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A {@link BeanRegistrationAotProcessor} processing beans for methods annotated with {@link TransactionalEventListener}
 * to register those methods' parameter types for reflection as they will need to be serialized for the event
 * publication registry.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
public class TransactionalEventListenerAotProcessor implements BeanRegistrationAotProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalEventListenerAotProcessor.class);

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.aot.BeanRegistrationAotProcessor#processAheadOfTime(org.springframework.beans.factory.support.RegisteredBean)
	 */
	@Override
	public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {

		Class<?> type = registeredBean.getBeanType().resolve(Object.class);

		var methods = Arrays.stream(type.getDeclaredMethods())
				.filter(it -> AnnotatedElementUtils.hasAnnotation(it, TransactionalEventListener.class))
				.toList();

		return methods.isEmpty() ? null : (context, __) -> {

			var reflection = context.getRuntimeHints().reflection();

			methods.forEach(method -> {

				for (var it : method.getParameterTypes()) {

					LOGGER.info("Registering {} (parameter of transactional event listener method {}) for reflection.",
							it.getSimpleName(), "%s.%s(…)".formatted(method.getDeclaringClass().getName(), method.getName()));

					reflection.registerType(it,
							MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
				}
			});
		};
	}
}
