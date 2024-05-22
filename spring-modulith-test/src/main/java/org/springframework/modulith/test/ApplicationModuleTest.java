/*
 * Copyright 2018-2024 the original author or authors.
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
package org.springframework.modulith.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.annotation.AliasFor;
import org.springframework.modulith.core.DependencyDepth;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Bootstraps the module containing the package of the test class annotated with {@link ApplicationModuleTest}. Will
 * apply the following modifications to the Spring Boot configuration:
 * <ul>
 * <li>Restricts the component scanning to the module's package.</li>
 * <li>Sets the module's package as the only auto-configuration and entity scan package.</li>
 * </ul>
 *
 * @author Oliver Drotbohm
 */
@Target(value = ElementType.TYPE)
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@TypeExcludeFilters(ModuleTypeExcludeFilter.class)
@ImportAutoConfiguration(ModuleTestAutoConfiguration.class)
@ExtendWith({ PublishedEventsParameterResolver.class, ScenarioParameterResolver.class })
@TestInstance(Lifecycle.PER_CLASS)
@TestConstructor(autowireMode = AutowireMode.ALL)
public @interface ApplicationModuleTest {

	@AliasFor("mode")
	BootstrapMode value() default BootstrapMode.STANDALONE;

	@AliasFor("value")
	BootstrapMode mode() default BootstrapMode.STANDALONE;

	/**
	 * Whether to automatically verify the module structure for validity.
	 *
	 * @return
	 */
	boolean verifyAutomatically() default true;

	/**
	 * Module names of modules to be included in the test run independent of what the {@link #mode()} defines.
	 *
	 * @return
	 */
	String[] extraIncludes() default {};

	/**
	 * Logical name of the module in case {@link ApplicationModuleTest} will be used outside a module package
	 */
	String module() default "";

	@AliasFor(annotation = SpringBootTest.class)
	Class<?>[] classes() default {};

	/**
	 * The type of web environment to create when applicable. Defaults to {@link WebEnvironment#MOCK}.
	 *
	 * @return the type of web environment
	 */
	@AliasFor(annotation = SpringBootTest.class)
	WebEnvironment webEnvironment() default WebEnvironment.MOCK;

	public enum BootstrapMode {

		/**
		 * Bootstraps the current module only.
		 */
		STANDALONE(DependencyDepth.NONE),

		/**
		 * Bootstraps the current module as well as its direct dependencies.
		 */
		DIRECT_DEPENDENCIES(DependencyDepth.IMMEDIATE),

		/**
		 * Bootstraps the current module as well as all upstream dependencies (including transitive ones).
		 */
		ALL_DEPENDENCIES(DependencyDepth.ALL);

		private final DependencyDepth depth;

		private BootstrapMode(DependencyDepth depth) {
			this.depth = depth;
		}

		/**
		 * Returns the {@link DependencyDepth} associated with the {@link BootstrapMode}.
		 *
		 * @return will never be {@literal null}.
		 */
		public DependencyDepth getDepth() {
			return depth;
		}
	}
}
