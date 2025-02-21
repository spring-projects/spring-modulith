/*
 * Copyright 2025 the original author or authors.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.util.Assert;

import com.jayway.jsonpath.JsonPath;

class PrecomputedApplicationModuleInitializerInvoker implements ApplicationModuleInitializerInvoker {

	private List<String> plan;

	public PrecomputedApplicationModuleInitializerInvoker(Resource metadata) {

		Assert.isTrue(metadata.exists(), () -> "Resource %s does not exist!".formatted(metadata.getDescription()));

		try (var stream = metadata.getInputStream()) {

			this.plan = JsonPath.parse(stream).<List<String>> read("$..initializers[*]");

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.runtime.autoconfigure.ApplicationModuleInitializerInvoker#invokeInitializers(java.util.stream.Stream)
	 */
	@Override
	public void invokeInitializers(Stream<ApplicationModuleInitializer> initializers) {

		var map = initializers
				.collect(Collectors.toMap(it -> it.getClass().getName(), Function.identity()));

		plan.stream()
				.map(map::get)
				.map(Optional::ofNullable)
				.flatMap(Optional::stream)
				.map(LoggingApplicationModuleInitializerAdapter::of)
				.forEach(ApplicationModuleInitializer::initialize);
	}
}
