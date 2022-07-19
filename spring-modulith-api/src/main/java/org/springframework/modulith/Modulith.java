/*
 * Copyright 2018-2022 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

/**
 * Defines a Spring Boot application to follow the Modulith structuring conventions.
 *
 * @author Oliver Drotbohm
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Modulithic
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { //
		@Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
public @interface Modulith {

	/**
	 * A logical system name for documentation purposes.
	 *
	 * @return
	 */
	@AliasFor(annotation = Modulithic.class)
	String systemName() default "";

	/**
	 * Whether to use fully qualified module names by default. If set to {@literal true}, hits will cause the module's
	 * default names to be their complete package name instead of just the modulith-local one. This might be useful in
	 * case {@link #additionalPackages()} pulls in packages that would cause module name conflicts, i.e. both root
	 * packages declare a local sub-package of the same name.
	 *
	 * @return
	 */
	@AliasFor(annotation = Modulithic.class)
	boolean useFullyQualifiedModuleNames() default false;

	/**
	 * The names of modules considered to be shared, i.e. which should always be included in the bootstrap no matter what.
	 * Useful for code to contain commons Spring configuration and components.
	 *
	 * @return
	 */
	@AliasFor(annotation = Modulithic.class)
	String[] sharedModules() default {};

	/**
	 * Defines which additional packages shall be considered as modulith base packages in addition to the one of the class
	 * carrying this annotation.
	 *
	 * @return
	 */
	@AliasFor(annotation = Modulithic.class)
	String[] additionalPackages() default {};
}
