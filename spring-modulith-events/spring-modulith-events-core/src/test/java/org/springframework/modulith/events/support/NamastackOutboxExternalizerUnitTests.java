/*
 * Copyright 2026 the original author or authors.
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

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Unit tests for {@link NamastackOutboxExternalizer}.
 */
class NamastackOutboxExternalizerUnitTests {

	EventExternalizationTransport transport = mock(EventExternalizationTransport.class);

	NamastackOutboxExternalizer handler = new NamastackOutboxExternalizer(
			new OutboxEventExternalizer(EventExternalizationConfiguration.externalizing()
					.select(EventExternalizationConfiguration.annotatedAsExternalized())
					.build(), mock(ApplicationEventPublisher.class), transport, new DefaultListableBeanFactory()),
			"handlerId");

	@Test
	void onlySupportsEventsSelectedForExternalization() {

		assertThat(handler.supports(new Sample(), null)).isTrue();
		assertThat(handler.supports(new NotExternalized(), null)).isFalse();
	}

	@Test
	void externalizesSupportedEvent() {

		when(transport.externalize(any(), any(RoutingTarget.class))).thenReturn(CompletableFuture.completedFuture(null));

		handler.handle(new Sample(), null);

		verify(transport).externalize(any(Sample.class), any(RoutingTarget.class));
	}

	@Test
	void usesGivenHandlerIdentifier() {
		assertThat(handler.getGenericHandlerIdentity().getId()).isEqualTo("handlerId");
	}

	@Externalized("target")
	record Sample() {}

	record NotExternalized() {}
}
