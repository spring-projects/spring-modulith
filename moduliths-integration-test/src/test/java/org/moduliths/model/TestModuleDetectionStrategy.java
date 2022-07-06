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
package org.moduliths.model;

import java.util.stream.Stream;

/**
 * @author Oliver Drotbohm
 */
class TestModuleDetectionStrategy implements ModuleDetectionStrategy {

	private final ModuleDetectionStrategy delegate = ModuleDetectionStrategy.directSubPackage();

	static boolean used;

	/*
	 * (non-Javadoc)
	 * @see org.moduliths.model.ModuleDetectionStrategy#getModuleBasePackages(org.moduliths.model.JavaPackage)
	 */
	@Override
	public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {

		TestModuleDetectionStrategy.used = true;

		return delegate.getModuleBasePackages(basePackage);
	}
}
