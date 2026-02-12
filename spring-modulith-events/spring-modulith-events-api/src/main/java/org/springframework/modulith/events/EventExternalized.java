/*
 * Copyright 2023-2026 the original author or authors.
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

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.util.Assert;

/**
 * An infrastructure event signaling that an application event has been externalized with a particular, broker-specific
 * result.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
public class EventExternalized<S, T> implements ResolvableTypeProvider {

	private final S event;
	private final Object mapped;
	private final RoutingTarget target;
	private final @Nullable T brokerResult;
	private final ResolvableType type;

	/**
	 * Creates a new {@link EventExternalized} event for the given source event, its mapped derivative,
	 * {@link RoutingTarget} and broker result.
	 *
	 * @param event must not be {@literal null}.
	 * @param mapped must not be {@literal null}.
	 * @param target must not be {@literal null}.
	 * @param brokerResult can be {@literal null}
	 */
	public EventExternalized(S event, Object mapped, RoutingTarget target, @Nullable T brokerResult) {

		Assert.notNull(event, "Source event must not be null!");
		Assert.notNull(mapped, "Mapped event must not be null!");
		Assert.notNull(target, "Routing target must not be null!");

		this.event = event;
		this.mapped = mapped;
		this.target = target;
		this.brokerResult = brokerResult;

		this.type = ResolvableType.forClassWithGenerics(EventExternalized.class, ResolvableType.forInstance(event),
				brokerResult == null ? ResolvableType.forClass(Object.class) : ResolvableType.forInstance(brokerResult));
	}

	/**
	 * Returns the source event.
	 *
	 * @return will never be {@literal null}.
	 */
	public S getEvent() {
		return event;
	}

	/**
	 * Returns the type of the source event.
	 *
	 * @return will never be {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	public Class<S> getEventType() {
		return (Class<S>) type.getGeneric(0).resolve(Object.class);
	}

	/**
	 * Returns the mapped event.
	 *
	 * @return will never be {@literal null}.
	 */
	public Object getMapped() {
		return mapped;
	}

	/**
	 * Returns the routing target.
	 *
	 * @return will never be {@literal null}.
	 */
	public RoutingTarget getTarget() {
		return target;
	}

	/**
	 * Returns the broker result.
	 *
	 * @return can be {@literal null}.
	 */
	public @Nullable T getBrokerResult() {
		return brokerResult;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.core.ResolvableTypeProvider#getResolvableType()
	 */
	@Override
	public @NonNull ResolvableType getResolvableType() {
		return type;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (obj == this) {
			return true;
		}

		if (!(obj instanceof EventExternalized that)) {
			return false;
		}

		return Objects.equals(this.event, that.event)
				&& Objects.equals(this.mapped, that.mapped)
				&& Objects.equals(this.brokerResult, that.brokerResult);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(this.event, this.mapped, this.brokerResult);
	}
}
