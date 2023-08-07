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

import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.events.EventPublicationRegistry;

/**
 * Unit tests for {@link PersistentApplicationEventMulticaster}.
 *
 * @author Oliver Drotbohm
 */
class PersistentApplicationEventMulticasterUnitTests {

	PersistentApplicationEventMulticaster multicaster;

	StandardEnvironment environment = new StandardEnvironment();
	EventPublicationRegistry registry = mock(EventPublicationRegistry.class);

	@BeforeEach
	void setUp() {
		this.multicaster = new PersistentApplicationEventMulticaster(() -> registry, () -> environment);
	}

	@Test // GH-240, GH-251
	void doesNotRepublishEventsOnRestartByDefault() {

		multicaster.afterSingletonsInstantiated();

		verify(registry, never()).findIncompletePublications();
	}

	@Test // GH-240, GH-251
	void triggersRepublicationIfExplicitlyEnabled() {

		var source = new MapPropertySource("test",
				Map.of(PersistentApplicationEventMulticaster.REPUBLISH_ON_RESTART, "true"));
		environment.getPropertySources().addFirst(source);

		multicaster.afterSingletonsInstantiated();

		verify(registry).findIncompletePublications();
	}
}
