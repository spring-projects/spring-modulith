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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Wrapper around {@link JavaClass} that allows creating additional formatted names.
 *
 * @author Oliver Drotbohm
 */
public class FormattableType {

	private static final Map<String, FormattableType> CACHE = new ConcurrentHashMap<>();

	private final String type;
	private final Supplier<String> abbreviatedName;

	/**
	 * Creates a new {@link FormattableType} for the given source {@link String} and lazily computed abbreviated name.
	 *
	 * @param type must not be {@literal null} or empty.
	 * @param abbreviatedName must not be {@literal null}.
	 */
	private FormattableType(String type, Supplier<String> abbreviatedName) {

		Assert.hasText(type, "Type string must not be null or empty!");
		Assert.notNull(abbreviatedName, "Computed abbreviated name must not be null!");

		this.type = type;
		this.abbreviatedName = abbreviatedName;
	}

	/**
	 * Creates a new {@link FormattableType} for the given fully-qualified type name.
	 *
	 * @param type must not be {@literal null} or empty.
	 */
	private FormattableType(String type) {

		Assert.hasText(type, "Type must not be null or empty!");

		this.type = type;
		this.abbreviatedName = SingletonSupplier.of(() -> {

			String abbreviatedPackage = Stream //
					.of(ClassUtils.getPackageName(type).split("\\.")) //
					.map(it -> it.substring(0, 1)) //
					.collect(Collectors.joining("."));

			return abbreviatedPackage.concat(".") //
					.concat(ClassUtils.getShortName(type));
		});
	}

	/**
	 * Creates a new {@link FormattableType} for the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static FormattableType of(JavaClass type) {

		Assert.notNull(type, "JavaClass must not be null!");

		return CACHE.computeIfAbsent(type.getName(), FormattableType::new);
	}

	/**
	 * Creates a new {@link FormattableType} for the given {@link Class}.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static FormattableType of(Class<?> type) {
		return CACHE.computeIfAbsent(type.getName(), FormattableType::new);
	}

	/**
	 * Formats the given {@link JavaClass}es by rendering a comma-separated list with the abbreviated class names.
	 *
	 * @param types must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static String format(Iterable<JavaClass> types) {

		Assert.notNull(types, "Types must not be null!");

		return StreamSupport.stream(types.spliterator(), false)
				.map(FormattableType::of)
				.map(FormattableType::getAbbreviatedFullName)
				.collect(Collectors.joining(", "));
	}

	/**
	 * Returns the abbreviated (i.e. every package fragment reduced to its first character) full name, e.g.
	 * {@code com.acme.MyType} will result in {@code c.a.MyType}.
	 *
	 * @return will never be {@literal null}.
	 */
	public String getAbbreviatedFullName() {
		return abbreviatedName.get();
	}

	/**
	 * Returns the abbreviated full name of the type abbreviating only the part of the given {@link ApplicationModule}'s
	 * base package.
	 *
	 * @param module can be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public String getAbbreviatedFullName(@Nullable ApplicationModule module) {

		if (module == null) {
			return getAbbreviatedFullName();
		}

		String basePackageName = module.getBasePackage().getName();

		if (!StringUtils.hasText(basePackageName)) {
			return getAbbreviatedFullName();
		}

		String typePackageName = ClassUtils.getPackageName(type);

		if (basePackageName.equals(typePackageName)) {
			return getAbbreviatedFullName();
		}

		if (!typePackageName.startsWith(basePackageName)) {
			return getFullName();
		}

		return abbreviate(basePackageName) //
				.concat(typePackageName.substring(basePackageName.length())) //
				.concat(".") //
				.concat(ClassUtils.getShortName(type));
	}

	/**
	 * Returns the type's full name.
	 *
	 * @return will never be {@literal null}.
	 */
	public String getFullName() {
		return type.replace("$", ".");
	}

	private static String abbreviate(String source) {

		return Stream //
				.of(source.split("\\.")) //
				.map(it -> it.substring(0, 1)) //
				.collect(Collectors.joining("."));
	}
}
