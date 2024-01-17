/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.modulith.events;

import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link EventExternalizationConfiguration}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class DefaultEventExternalizationConfiguration implements EventExternalizationConfiguration {

	private final Predicate<Object> filter;
	private final Function<Object, Object> mapper;
	private final Function<Object, RoutingTarget> router;

	/**
	 * Creates a new {@link DefaultEventExternalizationConfiguration}
	 *
	 * @param filter must not be {@literal null}.
	 * @param mapper must not be {@literal null}.
	 * @param router must not be {@literal null}.
	 */
	DefaultEventExternalizationConfiguration(Predicate<Object> filter, Function<Object, Object> mapper,
			Function<Object, RoutingTarget> router) {

		Assert.notNull(filter, "Filter must not be null!");
		Assert.notNull(mapper, "Mapper must not be null!");
		Assert.notNull(router, "Router must not be null!");

		this.filter = filter;
		this.mapper = mapper;
		this.router = router;
	}

	/**
	 * Returns a new {@link Selector} instance to build up a new configuration.
	 *
	 * @return will never be {@literal null}.
	 */
	static Selector builder() {
		return new Selector();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventExternalizationConfiguration#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(Object event) {

		Assert.notNull(event, "Event must not be null!");

		return filter.test(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventExternalizationConfiguration#map(java.lang.Object)
	 */
	@Override
	public Object map(Object event) {

		Assert.notNull(event, "Event must not be null!");

		return mapper.apply(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventExternalizationConfiguration#determineTarget(java.lang.Object)
	 */
	@Override
	public RoutingTarget determineTarget(Object event) {

		Assert.notNull(event, "Event must not be null!");

		return router.apply(event).verify();
	}
}
