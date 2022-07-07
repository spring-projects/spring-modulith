/*
 * Copyright 2020 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.Module;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Abstraction for low-level module information. Used to support different annotations to configure metadata about a
 * module.
 *
 * @author Oliver Drotbohm
 */
interface ModuleInformation {

	public static ModuleInformation of(JavaPackage javaPackage) {

		if (ClassUtils.isPresent("org.jmolecules.ddd.annotation.Module", ModuleInformation.class.getClassLoader())
				&& MoleculesModule.supports(javaPackage)) {
			return new MoleculesModule(javaPackage);
		}

		return new ModulithsModule(javaPackage);
	}

	String getDisplayName();

	List<String> getAllowedDependencies();

	@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
	static abstract class AbstractModuleInformation implements ModuleInformation {

		private final JavaPackage javaPackage;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleInformation#getName()
		 */
		@Override
		public String getDisplayName() {
			return javaPackage.getName();
		}
	}

	static class MoleculesModule extends AbstractModuleInformation {

		private final Optional<org.jmolecules.ddd.annotation.Module> annotation;

		public static boolean supports(JavaPackage javaPackage) {
			return javaPackage.getAnnotation(org.jmolecules.ddd.annotation.Module.class).isPresent();
		}

		public MoleculesModule(JavaPackage javaPackage) {

			super(javaPackage);

			this.annotation = javaPackage.getAnnotation(org.jmolecules.ddd.annotation.Module.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleInformation#getName()
		 */
		@Override
		public String getDisplayName() {

			return annotation //
					.map(org.jmolecules.ddd.annotation.Module::name) //
					.filter(StringUtils::hasText)
					.orElseGet(() -> annotation //
							.map(org.jmolecules.ddd.annotation.Module::value) //
							.filter(StringUtils::hasText) //
							.orElseGet(super::getDisplayName));
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleInformation#getAllowedDependencies()
		 */
		@Override
		public List<String> getAllowedDependencies() {
			return Collections.emptyList();
		}
	}

	static class ModulithsModule extends AbstractModuleInformation {

		private final Optional<Module> annotation;

		public static boolean supports(JavaPackage javaPackage) {
			return javaPackage.getAnnotation(Module.class).isPresent();
		}

		public ModulithsModule(JavaPackage javaPackage) {

			super(javaPackage);

			this.annotation = javaPackage.getAnnotation(Module.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleInformation.AbstractModuleInformation#getName()
		 */
		@Override
		public String getDisplayName() {

			return annotation //
					.map(Module::displayName) //
					.filter(StringUtils::hasText) //
					.orElseGet(super::getDisplayName);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleInformation#getAllowedDependencies()
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
