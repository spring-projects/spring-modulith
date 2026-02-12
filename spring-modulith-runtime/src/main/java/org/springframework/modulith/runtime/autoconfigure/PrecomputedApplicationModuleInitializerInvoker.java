/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.modulith.runtime.autoconfigure;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.util.Assert;

class PrecomputedApplicationModuleInitializerInvoker implements ApplicationModuleInitializerInvoker {

	private final List<String> initializerTypeNames;

	public PrecomputedApplicationModuleInitializerInvoker(ApplicationModuleMetadata metadata) {

		Assert.isTrue(metadata.isPresent(), "ApplicationModuleMetadata not present!");

		this.initializerTypeNames = metadata.getInitializerTypeNames();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.runtime.autoconfigure.ApplicationModuleInitializerInvoker#invokeInitializers(java.util.stream.Stream)
	 */
	@Override
	public void invokeInitializers(Stream<ApplicationModuleInitializer> initializers) {

		var map = initializers
				.collect(Collectors.toMap(it -> it.getClass().getName(), Function.identity()));

		triggerInitialization(initializerTypeNames.stream()
				.map(map::remove)
				.map(Optional::ofNullable)
				.flatMap(Optional::stream));

		triggerInitialization(map.values().stream());
	}

	private void triggerInitialization(Stream<ApplicationModuleInitializer> initializers) {

		initializers.map(LoggingApplicationModuleInitializerAdapter::of)
				.forEach(ApplicationModuleInitializer::initialize);
	}
}
