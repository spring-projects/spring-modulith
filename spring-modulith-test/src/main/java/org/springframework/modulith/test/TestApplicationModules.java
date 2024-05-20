/*
 * Copyright 2022-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ApplicationModulesFactory;
import org.springframework.modulith.core.ModulithMetadata;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Utility methods to work with test {@link ApplicationModules}. <em>Not intended public API!</em>
 *
 * @author Oliver Drotbohm
 */
public class TestApplicationModules {

	/**
	 * Creates an {@link ApplicationModules} instance from the given package but only inspecting the test code.
	 *
	 * @param basePackage must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModules of(String basePackage) {
		return of(ModulithMetadata.of(basePackage), basePackage);
	}

	/**
	 * Creates an {@link ApplicationModules} instance from the given application class but only inspecting the test code.
	 *
	 * @param applicationClass must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @since 1.2
	 */
	public static ApplicationModules of(Class<?> applicationClass) {
		return of(ModulithMetadata.of(applicationClass), applicationClass.getPackageName());
	}

	private static ApplicationModules of(ModulithMetadata metadata, String basePackage) {

		var packages = new ArrayList<>(List.of(basePackage));
		packages.addAll(metadata.getAdditionalPackages());

		return new ApplicationModules(metadata, packages, DescribedPredicate.alwaysFalse(), false,
				new ImportOption.OnlyIncludeTests()) {};
	}

	/**
	 * Custom {@link ApplicationModulesFactory} to bootstrap an {@link ApplicationModules} instance only considering test
	 * code.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.2
	 */
	static class Factory implements ApplicationModulesFactory {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.core.util.ApplicationModulesFactory#of(java.lang.Class)
		 */
		@Override
		public ApplicationModules of(Class<?> applicationClass) {
			return TestApplicationModules.of(applicationClass);
		}
	}
}
