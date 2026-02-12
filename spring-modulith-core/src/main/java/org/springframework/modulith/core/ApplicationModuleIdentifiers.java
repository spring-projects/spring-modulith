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
package org.springframework.modulith.core;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * {@link ApplicationModuleIdentifier}s that allow iteration in the order provided by the sources.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
public class ApplicationModuleIdentifiers implements Iterable<ApplicationModuleIdentifier> {

	private final Supplier<Stream<ApplicationModuleIdentifier>> source;

	/**
	 * Creates a new {@link ApplicationModuleIdentifiers} instance for the given source.
	 *
	 * @param source must not be {@literal null}.
	 */
	private ApplicationModuleIdentifiers(Supplier<Stream<ApplicationModuleIdentifier>> source) {

		Assert.notNull(source, "Source must not be null!");

		this.source = source;
	}

	/**
	 * Creates a new {@link ApplicationModuleIdentifiers} from the given ApplicationModules.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModuleIdentifiers of(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		return new ApplicationModuleIdentifiers(() -> modules.stream()
				.map(ApplicationModule::getIdentifier));
	}

	/**
	 * Creates a new {@link ApplicationModuleIdentifiers} for the given source identifiers.
	 *
	 * @param identifiers will never be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModuleIdentifiers of(List<ApplicationModuleIdentifier> identifiers) {
		return new ApplicationModuleIdentifiers(() -> identifiers.stream());
	}

	/**
	 * Creates a new {@link Stream} of {@link ApplicationModuleIdentifier}s.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModuleIdentifier> stream() {
		return source.get();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ApplicationModuleIdentifier> iterator() {
		return stream().iterator();
	}
}
