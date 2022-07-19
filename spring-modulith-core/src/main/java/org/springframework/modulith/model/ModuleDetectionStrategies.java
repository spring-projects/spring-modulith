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

import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.modulith.Module;
import org.springframework.modulith.model.Types.JMoleculesTypes;

/**
 * Default implementations of {@link ModuleDetectionStrategy}.
 *
 * @author Oliver Drotbohm
 * @see ModuleDetectionStrategy#directSubPackage()
 * @see ModuleDetectionStrategy#explictlyAnnotated()
 */
enum ModuleDetectionStrategies implements ModuleDetectionStrategy {

	DIRECT_SUB_PACKAGES {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleDetection#getModuleBasePackages(org.springframework.modulith.model.JavaPackage)
		 */
		@Override
		public Stream<JavaPackage> getModuleBasePackages(
				JavaPackage basePackage) {
			return basePackage.getDirectSubPackages().stream();
		}
	},

	EXPLICITLY_ANNOTATED {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ModuleDetection#getModuleBasePackages(org.springframework.modulith.model.JavaPackage)
		 */
		@Override
		public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {

			return Stream.of(Module.class, JMoleculesTypes.getModuleAnnotationTypeIfPresent())
					.filter(Objects::nonNull)
					.flatMap(basePackage::getSubPackagesAnnotatedWith);
		}
	}
}
