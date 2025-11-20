/*
 * Copyright 2017-2025 the original author or authors.
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

import java.time.Instant;

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
		assertThat(publication.getFailedAttempts()).isEmpty();
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
	@Test
	void isFailedAttemptStored() {

		var first = new SampleEvent("Foo");

		var identifier = PublicationTargetIdentifier.of("id");
		var publication = TargetEventPublication.of(first, identifier);

		assertThat(publication.getFailedAttempts()).isEmpty();

		Instant failedInstant = Instant.now();
		IllegalStateException reason = new IllegalStateException("test");

		publication.markFailed(failedInstant, reason);

		assertThat(publication.getFailedAttempts())
				.contains(new DefaultFailedAttemptInfo(failedInstant, reason));
	}

	record SampleEvent(String payload) {}
}
