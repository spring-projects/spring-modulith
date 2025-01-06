/*
 * Copyright 2023-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalEventListener;

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

		verify(registry).processIncompletePublications(any(), any(), any());
	}

	@Test // GH-240, GH-251, GH-823
	void triggersRepublicationIfLegacyConfigExplicitlyEnabled() {

		var source = new MapPropertySource("test",
				Map.of(PersistentApplicationEventMulticaster.REPUBLISH_ON_RESTART_LEGACY, "true"));
		environment.getPropertySources().addFirst(source);

		multicaster.afterSingletonsInstantiated();

		verify(registry).processIncompletePublications(any(), any(), any());
	}

	@Test // GH-277
	void honorsListenerCondition() throws Exception {

		try (var ctx = new AnnotationConfigApplicationContext()) {

			ctx.addBeanFactoryPostProcessor(new EventListenerMethodProcessor());
			ctx.registerBean("applicationEventMulticaster", ApplicationEventMulticaster.class, () -> multicaster);
			ctx.registerBean("conditionalListener", ConditionalListener.class);
			ctx.refresh();

			assertListenerSelected(new SampleEvent(true), true);
			assertListenerSelected(new SampleEvent(false), false);
		}
	}

	@Test // GH-726
	void onlyConsidersAfterCommitListeners() {

		var afterCommitListener = TransactionalApplicationListener.forPayload(TransactionPhase.AFTER_COMMIT, __ -> {});
		var beforeCommitListener = TransactionalApplicationListener.forPayload(TransactionPhase.BEFORE_COMMIT, __ -> {});

		var eventListeners = new PersistentApplicationEventMulticaster.TransactionalEventListeners(
				List.of(afterCommitListener, beforeCommitListener));

		assertThat(eventListeners.stream())
				.hasSize(1)
				.element(0).isEqualTo(afterCommitListener);
	}

	private void assertListenerSelected(SampleEvent event, boolean expected) {

		var listeners = multicaster.getApplicationListeners(new PayloadApplicationEvent<>(this, event),
				ResolvableType.forClass(event.getClass()));

		assertThat(listeners).hasSize(expected ? 1 : 0);
	}

	@Component
	static class ConditionalListener {

		@TransactionalEventListener(condition = "#event.supported")
		void on(SampleEvent event) {}
	}

	static class SampleEvent {
		public boolean supported;

		public SampleEvent(boolean supported) {
			this.supported = supported;
		}
	}
}
