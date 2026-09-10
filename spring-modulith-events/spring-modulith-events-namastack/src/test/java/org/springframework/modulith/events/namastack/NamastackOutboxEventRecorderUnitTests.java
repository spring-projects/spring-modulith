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
package org.springframework.modulith.events.namastack;

import static org.mockito.Mockito.*;

import io.namastack.outbox.Outbox;

import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.Externalized;

/**
 * Unit tests for {@link NamastackOutboxEventRecorder}.
 *
 * @author Oliver Drotbohm
 */
class NamastackOutboxEventRecorderUnitTests {

	@Test // GH-1861
	void evaluatesRoutingKeyExpressionAgainstEventPayload() {

		var configuration = EventExternalizationConfiguration.externalizing()
				.select(EventExternalizationConfiguration.annotatedAsExternalized())
				.build();

		var payload = new SampleEvent("value");
		var outbox = mock(Outbox.class);
		var recorder = new NamastackOutboxEventRecorder(configuration, outbox, new StandardEvaluationContext());

		recorder.onApplicationEvent(new PayloadApplicationEvent<>(this, payload));

		verify(outbox).schedule(payload, "value");
	}

	@Externalized("target::#{getValue()}")
	static class SampleEvent {

		private final String value;

		SampleEvent(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}
}
