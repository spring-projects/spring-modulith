/*
 * Copyright 2020-2025 the original author or authors.
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
package org.springframework.modulith.core;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Wrapper around {@link JavaClass} that allows creating additional formatted names.
 *
 * @author Oliver Drotbohm
 * @deprecated since 1.3, use {@link FormattableType} instead.
 */
@Deprecated
public abstract class FormatableType {

	/**
	 * Creates a new {@link FormatableType} for the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static FormatableType of(JavaClass type) {

		Assert.notNull(type, "JavaClass must not be null!");

		return FormattableType.of(type);
	}

	/**
	 * Creates a new {@link FormatableType} for the given {@link Class}.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static FormatableType of(Class<?> type) {
		return FormattableType.of(type);
	}

	/**
	 * Formats the given {@link JavaClass}es by rendering a comma-separated list with the abbreviated class names.
	 *
	 * @param types must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static String format(Iterable<JavaClass> types) {
		return FormattableType.format(types);
	}

	/**
	 * Returns the abbreviated (i.e. every package fragment reduced to its first character) full name, e.g.
	 * {@code com.acme.MyType} will result in {@code c.a.MyType}.
	 *
	 * @return will never be {@literal null}.
	 */
	public abstract String getAbbreviatedFullName();

	/**
	 * Returns the abbreviated full name of the type abbreviating only the part of the given {@link ApplicationModule}'s
	 * base package.
	 *
	 * @param module can be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public abstract String getAbbreviatedFullName(@Nullable ApplicationModule module);

	/**
	 * Returns the type's full name.
	 *
	 * @return will never be {@literal null}.
	 */
	public abstract String getFullName();
}
