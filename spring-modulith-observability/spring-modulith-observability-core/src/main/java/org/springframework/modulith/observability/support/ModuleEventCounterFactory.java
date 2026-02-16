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
package org.springframework.modulith.observability.support;

import io.micrometer.core.instrument.Counter.Builder;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.modulith.observability.ModulithEventMetrics;
import org.springframework.util.Assert;

/**
 * A factory to create {@link Builder} instances for {@link Counter}s eventually. Target for dependency injection via
 * the {@link org.springframework.modulith.observability.ModulithEventMetricsCustomizer} interface to allow users to
 * augment the counters with additional information.
 *
 * @author Oliver Drotbohm
 * @author Marcin Grzejszczak
 * @since 1.4
 */
public class ModuleEventCounterFactory implements ModulithEventMetrics {

	private static final ModulithMetricsCustomizer DEFAULT = new ModulithMetricsCustomizer(Object.class,
			ObservedModuleEvent::createBuilder);

	private final SortedSet<ModulithMetricsCustomizer> customizers = new TreeSet<>();
	private final SortedSet<ModulithMetricsCustomizer> creators = new TreeSet<>();

	/**
	 * Creates a {@link Builder} instance for the given event applying registered customizers.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Builder createCounterBuilder(ObservedModuleEvent event) {

		Assert.notNull(event, "Event must not be null!");

		var builder = creators.stream()
				.filter(it -> it.supports(event))
				.findFirst()
				.orElse(DEFAULT)
				.createBuilder(event);

		return customizers.stream()
				.sorted(Comparator.reverseOrder()) // Inverted order (most specific last)
				.filter(it -> it.supports(event))
				.reduce(builder, (it, customizer) -> customizer.augment(event, it), (l, r) -> r);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.api.ModulithEventMetricsCustomizer#customize(java.lang.Class, java.util.function.Function)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> ModulithEventMetrics customize(Class<T> type, Function<T, Builder> factory) {

		creators.add(new ModulithMetricsCustomizer(type, event -> factory.apply((T) event.getEvent())));

		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.api.ModulithEventMetricsCustomizer#customize(java.lang.Class, java.util.function.BiConsumer)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> ModuleEventCounterFactory customize(Class<T> type, BiConsumer<T, Builder> consumer) {

		customizers.add(new ModulithMetricsCustomizer(type, (BiConsumer<Object, Builder>) consumer));

		return this;
	}

	private static class ModulithMetricsCustomizer implements Comparable<ModulithMetricsCustomizer> {

		private final Class<?> type;
		private final Function<ObservedModuleEvent, Builder> creator;
		private final BiFunction<Object, Builder, Builder> customizer;

		ModulithMetricsCustomizer(Class<?> type, Function<ObservedModuleEvent, Builder> creator) {

			this.type = type;
			this.creator = creator;
			this.customizer = (event, builder) -> builder;
		}

		ModulithMetricsCustomizer(Class<?> type, BiConsumer<Object, Builder> customizer) {

			this.type = type;
			this.creator = ObservedModuleEvent::createBuilder;
			this.customizer = (event, builder) -> {
				customizer.accept(event, builder);
				return builder;
			};
		}

		Builder createBuilder(ObservedModuleEvent event) {
			return creator.apply(event);
		}

		boolean supports(ObservedModuleEvent event) {
			return event.hasEventOfType(type);
		}

		Builder augment(ObservedModuleEvent event, Builder builder) {
			return customizer.apply(event.getEvent(), builder);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(ModulithMetricsCustomizer that) {

			if (this.type.isAssignableFrom(that.type)) {
				return 1;
			}
			if (that.type.isAssignableFrom(this.type)) {
				return -1;
			}

			// If classes are not in the same hierarchy, sort by name for consistency
			return this.type.getName().compareTo(that.type.getName());
		}
	}
}
