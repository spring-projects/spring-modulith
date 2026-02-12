/*
 * Copyright 2020-2026 the original author or authors.
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

import java.util.List;
import java.util.Objects;

import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * A Spring bean type.
 *
 * @author Oliver Drotbohm
 */
public class SpringBean {

	private final JavaClass type;
	private final ApplicationModule module;

	/**
	 * Creates a new {@link SpringBean} for the given {@link JavaClass} and {@link ApplicationModule}.
	 *
	 * @param type must not be {@literal null}.
	 * @param module must not be {@literal null}.
	 */
	private SpringBean(JavaClass type, ApplicationModule module) {

		Assert.notNull(type, "JavaClass must not be null!");
		Assert.notNull(module, "ApplicationModule must not be null!");

		this.type = type;
		this.module = module;
	}

	/**
	 * Creates a new {@link SpringBean} for the given {@link JavaClass} and {@link ApplicationModule}.
	 *
	 * @param type must not be {@literal null}.
	 * @param module must not be {@literal null}.
	 */
	static SpringBean of(JavaClass type, ApplicationModule module) {
		return new SpringBean(type, module);
	}

	/**
	 * Returns the {@link JavaClass} of the {@link SpringBean}.
	 *
	 * @return will never be {@literal null}.
	 */
	public JavaClass getType() {
		return type;
	}

	/**
	 * Returns the fully-qualified name of the Spring bean type.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	public String getFullyQualifiedTypeName() {
		return type.getFullName();
	}

	/**
	 * Returns whether the bean is considered to be an API bean, which means it is either a public type exposed in an API
	 * package of the module or implements an exposed API interface.
	 */
	public boolean isApiBean() {

		return module.isExposed(type)
				|| getInterfacesWithinModule().stream().anyMatch(module::isExposed);
	}

	/**
	 * Returns all interfaces implemented by the bean that are part of the same application module.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<JavaClass> getInterfacesWithinModule() {

		return type.getRawInterfaces().stream() //
				.filter(module::contains) //
				.toList();
	}

	/**
	 * Creates a new {@link ArchitecturallyEvidentType} from the current {@link SpringBean}.
	 *
	 * @return
	 */
	public ArchitecturallyEvidentType toArchitecturallyEvidentType() {
		return ArchitecturallyEvidentType.of(type, module.getSpringBeansInternal());
	}

	/**
	 * Returns whether the bean is assignable to the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @since 1.4
	 */
	public boolean isAssignableTo(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return this.type.isAssignableTo(type);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof SpringBean that)) {
			return false;
		}

		return Objects.equals(this.module, that.module) //
				&& Objects.equals(this.type, that.type);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(type, module);
	}
}
