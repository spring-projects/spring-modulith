/*
 * Copyright 2020-2022 the original author or authors.
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * A Spring bean type.
 *
 * @author Oliver Drotbohm
 */
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "of", access = AccessLevel.PACKAGE)
public class SpringBean {

	private final @Getter JavaClass type;
	private final ApplicationModule module;

	/**
	 * Returns the fully-qualified name of the Spring bean type.
	 *
	 * @return
	 */
	public String getFullyQualifiedTypeName() {
		return type.getFullName();
	}

	/**
	 * Returns all interfaces implemented by the bean that are part of the same module.
	 *
	 * @return
	 */
	public List<JavaClass> getInterfacesWithinModule() {

		return type.getRawInterfaces().stream() //
				.filter(module::contains) //
				.collect(Collectors.toList());
	}

	public boolean isAnnotatedWith(Class<?> type) {
		return Types.isAnnotatedWith(type).apply(this.type);
	}

	public ArchitecturallyEvidentType toArchitecturallyEvidentType() {
		return ArchitecturallyEvidentType.of(type, module.getSpringBeansInternal());
	}
}
