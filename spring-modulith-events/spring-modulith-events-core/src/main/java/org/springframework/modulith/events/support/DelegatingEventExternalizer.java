/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.modulith.events.support;

import java.util.function.BiConsumer;

import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * An {@link EventExternalizationSupport} delegating to a {@link BiConsumer} for the actual externalization. Note, that
 * this <em>needs</em> to be a {@link Component} to make sure it is considered an event listener, as without the
 * annotation Spring Framework would skip it as it lives in the {@code org.springframework} package.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
@Component
public class DelegatingEventExternalizer extends EventExternalizationSupport {

	private final BiConsumer<RoutingTarget, Object> delegate;

	/**
	 * Creates a new {@link DelegatingEventExternalizer} for the given {@link EventExternalizationConfiguration} and
	 * {@link BiConsumer} implementing the actual externalization.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param delegate must not be {@literal null}.
	 */
	public DelegatingEventExternalizer(EventExternalizationConfiguration configuration,
			BiConsumer<RoutingTarget, Object> delegate) {

		super(configuration);

		Assert.notNull(delegate, "BiConsumer must not be null!");

		this.delegate = delegate;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.support.EventExternalizationSupport#externalize(java.lang.Object)
	 */
	@Override
	@ApplicationModuleListener
	public void externalize(Object event) {
		super.externalize(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.support.EventExternalizationSupport#externalize(org.springframework.modulith.events.RoutingTarget, java.lang.Object)
	 */
	@Override
	protected void externalize(Object payload, RoutingTarget target) {
		delegate.accept(target, payload);
	}
}
