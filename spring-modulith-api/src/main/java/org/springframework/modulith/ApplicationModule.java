/*
 * Copyright 2018-2025 the original author or authors.
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
package org.springframework.modulith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to customize information of a {@link Modulith} module.
 *
 * @author Oliver Drotbohm
 */
@PackageInfo
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplicationModule {

	public static final String OPEN_TOKEN = "¯\\_(ツ)_/¯";

	/**
	 * The identifier of the module. Must not contain a double colon ({@code ::}).
	 *
	 * @return will never be {@literal null}.
	 */
	String id() default "";

	/**
	 * The human readable name of the module to be used for display and documentation purposes.
	 *
	 * @return will never be {@literal null}.
	 */
	String displayName() default "";

	/**
	 * List the names of modules that the module is allowed to depend on. Shared modules defined in
	 * {@link Modulith}/{@link Modulithic} will be allowed, too. Names listed are local ones, unless the application has
	 * configured {@link Modulithic#useFullyQualifiedModuleNames()} to {@literal true}. Explicit references to
	 * {@link NamedInterface}s need to be separated by a double colon {@code ::}, e.g. {@code module::API} if
	 * {@code module} is the logical module name and {@code API} is the name of the named interface.
	 * <p>
	 * Declaring an empty array will allow no dependencies to other modules. To not restrict the dependencies at all,
	 * leave the attribute at its default value.
	 *
	 * @return will never be {@literal null}.
	 * @see NamedInterface
	 */
	String[] allowedDependencies() default { OPEN_TOKEN };

	/**
	 * Declares the {@link Type} of the {@link ApplicationModule}
	 *
	 * @return will never be {@literal null}.
	 * @since 1.2
	 */
	Type type() default Type.CLOSED;

	/**
	 * The type of an application module
	 *
	 * @author Oliver Drotbohm
	 * @since 1.2
	 */
	enum Type {

		/**
		 * A closed application module exposes an API to other modules, but also allows to hide internals. Access to those
		 * internals from other modules is sanctioned. Also, closed application modules must not be part of dependency
		 * cycles.
		 *
		 * @see NamedInterface
		 */
		CLOSED,

		/**
		 * An open application module does not hide its internals, which means that access to those from other modules is
		 * not sanctioned. They are also excluded from the cycle detection algorithm. All types contained in an open module
		 * are part of the unnamed named interface.
		 */
		OPEN;
	}
}
