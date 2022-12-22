/*
 * Copyright 2022 the original author or authors.
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

import java.util.List;

import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.model.ModulithMetadata;

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
	 * @return
	 */
	public static ApplicationModules of(String basePackage) {
		return new ApplicationModules(ModulithMetadata.of(basePackage), List.of(basePackage),
				DescribedPredicate.alwaysFalse(), false, new ImportOption.OnlyIncludeTests()) {};
	}
}
