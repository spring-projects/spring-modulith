/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.docs.metadata;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Metadata about a Java {@link Method}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
public record MethodMetadata(String name, String signature, @Nullable String comment) {

	/**
	 * Returns whether the method represented has the same signature as the given one.
	 *
	 * @param method must not be {@literal null}.
	 * @return
	 */
	public boolean hasSignatureOf(Method method) {

		Assert.notNull(method, "Method must not be null!");

		var parameters = Arrays.stream(method.getParameterTypes())
				.map(Class::getName)
				.collect(Collectors.joining(", ", "(", ")"));

		return signature.equals(method.getName().concat(parameters));
	}
}
