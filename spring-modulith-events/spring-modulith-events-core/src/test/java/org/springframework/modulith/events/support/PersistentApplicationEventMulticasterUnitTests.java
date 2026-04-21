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
package org.springframework.modulith.events.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.modulith.events.support.PersistentApplicationEventMulticaster.TransactionalEventListeners.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.support.PersistentApplicationEventMulticaster.TransactionalEventListeners;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.util.ReflectionUtils;

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

	@Test // GH-277, GH-1654
	void honorsListenerCondition() throws Exception {

		try (var ctx = new AnnotationConfigApplicationContext()) {

			ctx.addBeanFactoryPostProcessor(new EventListenerMethodProcessor());
			ctx.registerBean(TransactionalEventListenerFactory.class, TransactionalEventListenerFactory::new);
			ctx.registerBean("applicationEventMulticaster", ApplicationEventMulticaster.class, () -> multicaster);
			ctx.registerBean("unconditionalListener", UnconditionalListener.class, UnconditionalListener::new);
			ctx.registerBean("conditionalListener", ConditionalListener.class, ConditionalListener::new);
			ctx.refresh();

			multicast(new SampleEvent(false));
			multicast(new SampleEvent(true));

			@SuppressWarnings("unchecked")
			ArgumentCaptor<Stream<PublicationTargetIdentifier>> captor = ArgumentCaptor.forClass(Stream.class);
			verify(registry, times(2)).store(any(), captor.capture());

			var allValues = captor.getAllValues();

			assertThat(allValues.get(0).count()).isEqualTo(1L);
			assertThat(allValues.get(1).count()).isEqualTo(2L);
		}
	}

	private void multicast(Object event) {
		multicaster.multicastEvent(new PayloadApplicationEvent<>(this, event));
	}

	@Test // GH-726
	void onlyConsidersAfterCommitListeners() {

		var afterCommitListener = TransactionalApplicationListener.forPayload(TransactionPhase.AFTER_COMMIT, __ -> {});
		var beforeCommitListener = TransactionalApplicationListener.forPayload(TransactionPhase.BEFORE_COMMIT, __ -> {});

		var eventListeners = new TransactionalEventListeners(List.of(afterCommitListener, beforeCommitListener),
				() -> environment);

		assertThat(eventListeners.stream())
				.hasSize(1)
				.element(0).isEqualTo(afterCommitListener);
	}

	@Test // GH-1630
	void considersTriggerAnnotation() {

		var environment = new MockEnvironment();
		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, ApplicationModuleListener.class.getName());

		var first = getAdapter(ConditionalListener.class, "on", SampleEvent.class);
		var second = getAdapter(ModuleListener.class, "on", SampleEvent.class);

		var listeners = new TransactionalEventListeners(List.of(first, second),
				() -> environment);

		assertThat(listeners.stream()).containsExactly(second);
	}

	@Test // GH-1630
	void rejectsNotLoadableTriggerAnnotation() {

		var environment = new MockEnvironment();
		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, "some.non.loadable.Type");

		var second = getAdapter(ModuleListener.class, "on", SampleEvent.class);

		assertThatIllegalStateException().isThrownBy(() -> {
			new TransactionalEventListeners(List.of(second), () -> environment);
		});
	}

	@Test // GH-1630
	void rejectsNonAnnotationTypeForTriggerAnnotation() {

		var environment = new MockEnvironment();
		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, "java.lang.String");

		var second = getAdapter(ModuleListener.class, "on", SampleEvent.class);

		assertThatIllegalStateException().isThrownBy(() -> {
			new TransactionalEventListeners(List.of(second), () -> environment);
		});
	}

	private static TransactionalApplicationListenerMethodAdapter getAdapter(Class<?> type, String methodName,
			Class<?> parameter) {

		var method = ReflectionUtils.findMethod(type, methodName, parameter);

		return new TransactionalApplicationListenerMethodAdapter(type.getName(), type, method);
	}

	@Component
	static class UnconditionalListener {

		boolean invoked = false;

		@TransactionalEventListener
		void on(SampleEvent event) {
			this.invoked = true;
		}
	}

	@Component
	static class ConditionalListener {

		boolean invoked = false;

		@TransactionalEventListener(condition = "#event.supported")
		void on(SampleEvent event) {
			this.invoked = true;
		}
	}

	@Component
	static class ModuleListener {

		@ApplicationModuleListener
		void on(SampleEvent event) {}
	}

	static class SampleEvent {
		public boolean supported;

		public SampleEvent(boolean supported) {
			this.supported = supported;
		}
	}
}
