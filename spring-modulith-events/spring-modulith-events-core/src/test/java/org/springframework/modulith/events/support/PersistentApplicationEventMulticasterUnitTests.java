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

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

/**
 * Unit tests for {@link PersistentApplicationEventMulticaster}.
 *
 * @author Oliver Drotbohm
 * @author Seonwoo Jung
 */
class PersistentApplicationEventMulticasterUnitTests {

	PersistentApplicationEventMulticaster multicaster;

	StandardEnvironment environment = new StandardEnvironment();
	EventPublicationRegistry registry = mock(EventPublicationRegistry.class);

	@BeforeEach
	void setUp() {
		this.multicaster = new PersistentApplicationEventMulticaster(() -> registry, () -> environment);
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

	@Test // GH-1783
	void doesNotPropagateClassCastExceptionOfNonMatchingLambdaListener() {

		multicaster.addApplicationListener(ApplicationListener.forPayload(__ -> {}));

		assertThatNoException()
				.isThrownBy(() -> multicaster.multicastEvent(new SampleApplicationEvent(this)));
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

	@SuppressWarnings("serial")
	static class SampleApplicationEvent extends ApplicationEvent {

		SampleApplicationEvent(Object source) {
			super(source);
		}
	}

	static class SampleEvent {
		public boolean supported;

		public SampleEvent(boolean supported) {
			this.supported = supported;
		}
	}
}
