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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.ApplicationModule;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Abstraction for low-level module information. Used to support different annotations to configure metadata about a
 * module.
 *
 * @author Oliver Drotbohm
 */
interface ApplicationModuleInformation {

	public static ApplicationModuleInformation of(JavaPackage javaPackage) {

		if (ClassUtils.isPresent("org.jmolecules.ddd.annotation.Module",
				ApplicationModuleInformation.class.getClassLoader())
				&& MoleculesModule.supports(javaPackage)) {
			return new MoleculesModule(javaPackage);
		}

		return new ModulithsModule(javaPackage);
	}

	default Optional<String> getDisplayName() {
		return Optional.empty();
	}

	List<String> getAllowedDependencies();

	static class MoleculesModule implements ApplicationModuleInformation {

		private final Optional<org.jmolecules.ddd.annotation.Module> annotation;

		public static boolean supports(JavaPackage javaPackage) {
			return javaPackage.getAnnotation(org.jmolecules.ddd.annotation.Module.class).isPresent();
		}

		public MoleculesModule(JavaPackage javaPackage) {
			this.annotation = javaPackage.getAnnotation(org.jmolecules.ddd.annotation.Module.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ApplicationModuleInformation#getDisplayName()
		 */
		@Override
		public Optional<String> getDisplayName() {

			Supplier<Optional<String>> fallback = () -> annotation //
					.map(org.jmolecules.ddd.annotation.Module::value) //
					.filter(StringUtils::hasText);

			return annotation //
					.map(org.jmolecules.ddd.annotation.Module::name) //
					.filter(StringUtils::hasText)
					.or(fallback);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ApplicationModuleInformation#getAllowedDependencies()
		 */
		@Override
		public List<String> getAllowedDependencies() {
			return Collections.emptyList();
		}
	}

	static class ModulithsModule implements ApplicationModuleInformation {

		private final Optional<ApplicationModule> annotation;

		public static boolean supports(JavaPackage javaPackage) {
			return javaPackage.getAnnotation(ApplicationModule.class).isPresent();
		}

		public ModulithsModule(JavaPackage javaPackage) {
			this.annotation = javaPackage.getAnnotation(ApplicationModule.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ApplicationModuleInformation#getDisplayName()
		 */
		@Override
		public Optional<String> getDisplayName() {

			return annotation //
					.map(ApplicationModule::displayName) //
					.filter(StringUtils::hasText);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ApplicationModuleInformation#getAllowedDependencies()
		 */
		@Override
		public List<String> getAllowedDependencies() {

			return annotation //
					.map(it -> Arrays.stream(it.allowedDependencies())) //
					.orElse(Stream.empty()) //
					.collect(Collectors.toList());
		}
	}
}
