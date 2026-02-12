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
package org.springframework.modulith.observability;

import io.micrometer.core.instrument.Counter.Builder;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SPI to customize the {@link io.micrometer.core.instrument.Counter} instances created for cross-module application
 * events.
 *
 * @author Oliver Drotbohm
 * @author Marcin Grzejszczak
 * @since 1.4
 */
public interface ModulithEventMetrics {

	/**
	 * Customizes a {@link io.micrometer.core.instrument.Counter.Builder} to eventually produce a
	 * {@link io.micrometer.core.instrument.Counter} for the event of the given type. The {@link Builder} will have been
	 * set up named after the fully-qualified type name. To customize the creation, also call
	 * {@link #customize(Class, Function)}.
	 *
	 * @param <T> the type of the event.
	 * @param type must not be {@literal null}.
	 * @param consumer must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @see #customize(Class, Function)
	 */
	<T> ModulithEventMetrics customize(Class<T> type, BiConsumer<T, Builder> consumer);

	/**
	 * Customizes the creation of a {@link Builder} for events of the given type. The instances created will still be
	 * subject to customizations registered via {@link #customize(Class, BiConsumer)}.
	 *
	 * @param <T>
	 * @param type must not be {@literal null}.
	 * @param factory must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	<T> ModulithEventMetrics customize(Class<T> type, Function<T, Builder> factory);
}
