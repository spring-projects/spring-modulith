/*
 * Copyright 2020-2024 the original author or authors.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCodeUnit;

/**
 * A {@link Source} backed by an ArchUnit {@link JavaAccess}.
 *
 * @author Oliver Drotbohm
 */
class JavaAccessSource implements Source {

	private final static Pattern LAMBDA_EXTRACTOR = Pattern.compile("lambda\\$(.*)\\$.*");

	private final FormattableType type;
	private final JavaCodeUnit method;
	private final String name;

	/**
	 * Creates a new {@link JavaAccessSource} for the given {@link JavaAccess}.
	 *
	 * @param access must not be {@literal null}.
	 */
	public JavaAccessSource(JavaAccess<?> access) {

		this.type = FormattableType.of(access.getOriginOwner());
		this.method = access.getOrigin();

		String name = method.getName();
		Matcher matcher = LAMBDA_EXTRACTOR.matcher(name);

		this.name = matcher.matches() ? matcher.group(1) : name;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.Source#toString(org.springframework.modulith.model.Module)
	 */
	@Override
	public String toString(ApplicationModule module) {

		boolean noParameters = method.getRawParameterTypes().isEmpty();

		return String.format("%s.%s(%s)", type.getAbbreviatedFullName(module), name, noParameters ? "" : "…");
	}
}
