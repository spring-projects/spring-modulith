/*
 * Copyright 2020-2023 the original author or authors.
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
package org.springframework.modulith.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * Wrapper around {@link JavaClass} that allows creating additional formatted names.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class FormatableJavaClass {

	private static final Map<JavaClass, FormatableJavaClass> CACHE = new ConcurrentHashMap<>();

	private final JavaClass type;
	private final Supplier<String> abbreviatedName;

	public static FormatableJavaClass of(JavaClass type) {
		return CACHE.computeIfAbsent(type, FormatableJavaClass::new);
	}

	private FormatableJavaClass(JavaClass type) {

		Assert.notNull(type, "JavaClass must not be null!");

		this.type = type;
		this.abbreviatedName = Suppliers.memoize(() -> {

			String abbreviatedPackage = Stream //
					.of(type.getPackageName().split("\\.")) //
					.map(it -> it.substring(0, 1)) //
					.collect(Collectors.joining("."));

			return abbreviatedPackage.concat(".") //
					.concat(ClassUtils.getShortName(getFullName()));
		});
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

	public String getAbbreviatedFullName(@Nullable ApplicationModule module) {

		if (module == null) {
			return getAbbreviatedFullName();
		}

		String basePackageName = module.getBasePackage().getName();

		if (!StringUtils.hasText(basePackageName)) {
			return getAbbreviatedFullName();
		}

		String typePackageName = type.getPackageName();

		if (basePackageName.equals(typePackageName)) {
			return getAbbreviatedFullName();
		}

		if (!typePackageName.startsWith(basePackageName)) {
			return getFullName();
		}

		return abbreviate(basePackageName) //
				.concat(typePackageName.substring(basePackageName.length())) //
				.concat(".") //
				.concat(ClassUtils.getShortName(getFullName()));
	}

	/**
	 * Returns the type's full name.
	 *
	 * @return will never be {@literal null}.
	 */
	public String getFullName() {
		return type.getName().replace("$", ".");
	}

	private static String abbreviate(String source) {

		return Stream //
				.of(source.split("\\.")) //
				.map(it -> it.substring(0, 1)) //
				.collect(Collectors.joining("."));
	}
}
