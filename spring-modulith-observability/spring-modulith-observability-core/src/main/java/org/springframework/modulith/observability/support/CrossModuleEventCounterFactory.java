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
package org.springframework.modulith.observability.support;

import io.micrometer.core.instrument.Counter;
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
public class CrossModuleEventCounterFactory implements ModulithEventMetrics {

	private final SortedSet<ModulithMetricsCustomizer> customizers = new TreeSet<>();
	private final SortedSet<ModulithMetricsCustomizer> creators = new TreeSet<>();

	/**
	 * Creates a {@link Builder} instance for the given event applying registered customizers.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Builder createCounterBuilder(Object event) {

		Assert.notNull(event, "Event must not be null!");

		// Use most specific creator (default order as defined in ModulithMetricsCustomizer)
		var creator = creators.stream()
				.filter(it -> it.supports(event))
				.findFirst()
				.orElse(ModulithMetricsCustomizer.DEFAULT);

		var builder = creator.createBuilder(event);

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

		creators.add(new ModulithMetricsCustomizer(type, (Function<Object, Builder>) factory));
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.api.ModulithEventMetricsCustomizer#customize(java.lang.Class, java.util.function.BiConsumer)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> CrossModuleEventCounterFactory customize(Class<T> type, BiConsumer<T, Builder> consumer) {

		customizers.add(new ModulithMetricsCustomizer(type, (BiConsumer<Object, Builder>) consumer));
		return this;
	}

	private static class ModulithMetricsCustomizer implements Comparable<ModulithMetricsCustomizer> {

		private static final BiConsumer<Object, Builder> NO_OP = (event, builder) -> {};
		private static final Function<Object, Builder> DEFAULT_FACTORY = event -> Counter
				.builder(event.getClass().getSimpleName());

		public static final ModulithMetricsCustomizer DEFAULT = new ModulithMetricsCustomizer(Object.class, NO_OP);

		private final Class<?> type;
		private final Function<Object, Builder> creator;
		private final BiFunction<Object, Builder, Builder> customizer;

		public ModulithMetricsCustomizer(Class<?> type, Function<Object, Builder> creator) {

			this.type = type;
			this.creator = creator;
			this.customizer = (event, builder) -> builder;
		}

		public ModulithMetricsCustomizer(Class<?> type, BiConsumer<Object, Builder> creator) {

			this.type = type;
			this.creator = DEFAULT_FACTORY;
			this.customizer = (event, builder) -> {
				creator.accept(event, builder);
				return builder;
			};
		}

		public Builder createBuilder(Object event) {
			return creator.apply(event);
		}

		public boolean supports(Object event) {
			return type.isInstance(event);
		}

		public Builder augment(Object event, Builder builder) {
			return customizer.apply(event, builder);
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
