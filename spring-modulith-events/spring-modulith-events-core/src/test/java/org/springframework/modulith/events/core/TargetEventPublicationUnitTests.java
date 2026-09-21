/*
 * Copyright 2017-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;

/**
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
class TargetEventPublicationUnitTests {

	@Test
	void rejectsNullEvent() {

		assertThatExceptionOfType(IllegalArgumentException.class)//
				.isThrownBy(() -> TargetEventPublication.of(null, PublicationTargetIdentifier.of("foo")))//
				.withMessageContaining("Event");
	}

	@Test
	void rejectsNullTargetIdentifier() {

		assertThatExceptionOfType(IllegalArgumentException.class)//
				.isThrownBy(() -> TargetEventPublication.of(new Object(), null))//
				.withMessageContaining("TargetIdentifier");
	}

	@Test
	void publicationIsIncompleteByDefault() {

		var publication = TargetEventPublication.of(new Object(),
				PublicationTargetIdentifier.of("foo"));

		assertThat(publication.isCompleted()).isFalse();
		assertThat(publication.getCompletionDate()).isNotPresent();
	}

	@Test // GH-1056
	void isOnlyAssociatedWithTheVerySameEventInstance() {

		var first = new SampleEvent("Foo");
		var second = new SampleEvent("Foo");

		var identifier = PublicationTargetIdentifier.of("id");
		var publication = TargetEventPublication.of(first, identifier);

		assertThat(publication.isAssociatedWith(first, identifier)).isTrue();
		assertThat(publication.isAssociatedWith(second, identifier)).isFalse();
	}

	@Test // GH-1565
	void wrapsPlainPayloadIntoPayloadApplicationEvent() {

		var event = new SampleEvent("Foo");
		var publication = TargetEventPublication.of(event, PublicationTargetIdentifier.of("id"));

		var applicationEvent = publication.getApplicationEvent();

		assertThat(applicationEvent).isInstanceOf(PayloadApplicationEvent.class);
		assertThat(((PayloadApplicationEvent<?>) applicationEvent).getPayload()).isEqualTo(event);
	}

	@Test // GH-1565
	void doesNotRewrapEventThatAlreadyIsAnApplicationEvent() {

		var event = new SampleApplicationEvent(this);
		var publication = TargetEventPublication.of(event, PublicationTargetIdentifier.of("id"));

		assertThat(publication.getApplicationEvent()).isSameAs(event);
	}

	record SampleEvent(String payload) {}

	@SuppressWarnings("serial")
	static class SampleApplicationEvent extends ApplicationEvent {

		public SampleApplicationEvent(Object source) {
			super(source);
		}
	}
}
