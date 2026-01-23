/*
 * Copyright 2026 the original author or authors.
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

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.core.annotation.AliasFor;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Foundation for tests that would want to bootstrap tests slicing the application per application module. Furthermore
 * it defines
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Roey Marquis II - Shadows Within - The Urban Monk (Original Soundtrack)
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
@TypeExcludeFilters(ModuleTypeExcludeFilter.class)
@ImportAutoConfiguration(ModuleTestAutoConfiguration.class)
@ExtendWith({ PublishedEventsParameterResolver.class, ScenarioParameterResolver.class })
@TestInstance(Lifecycle.PER_CLASS)
@TestConstructor(autowireMode = AutowireMode.ALL)
public @interface ModuleSlicing {

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
	 * Logical name of the module to be bootstrapped in case {@link ApplicationModuleTest} will be used outside a module
	 * package
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	String module() default "";

	/**
	 * To define the main application class in case the test is located outside a module package and that class doesn't
	 * reside in any of the parent packages.
	 *
	 * @return will never be {@literal null}.
	 * @see #module()
	 * @since 1.3
	 */
	Class<?>[] classes() default {};
}
