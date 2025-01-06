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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.config.EnablePersistentDomainEvents;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Integration test for {@link PersistentApplicationEventMulticaster}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(SpringExtension.class)
class PersistentApplicationEventMulticasterIntegrationTests {

	@Configuration
	@EnableTransactionManagement
	@EnablePersistentDomainEvents
	static class TestConfiguration {

		@Bean
		EventPublicationRepository repository() {
			return mock(EventPublicationRepository.class);
		}

		@Bean
		SampleEventListener listener() {
			return new SampleEventListener();
		}
	}

	@Autowired ApplicationEventPublisher publisher;
	@Autowired EventPublicationRepository repository;

	@Test // GH-186, GH-239
	void doesNotPublishGenericEventsToListeners() throws Exception {

		publisher.publishEvent(new SomeGenericEvent<>());
		verify(repository, never()).create(any(TargetEventPublication.class));

		publisher.publishEvent(new SomeOtherEvent());
		verify(repository).create(any(TargetEventPublication.class));
	}

	@Component
	static class SampleEventListener {

		@TransactionalEventListener
		void listener(SomeOtherEvent event) {}
	}

	static class SomeGenericEvent<T> extends ApplicationEvent {

		private static final long serialVersionUID = -4054955298417761460L;

		public SomeGenericEvent() {
			super(new Object());
		}
	}

	static class SomeOtherEvent {}
}
