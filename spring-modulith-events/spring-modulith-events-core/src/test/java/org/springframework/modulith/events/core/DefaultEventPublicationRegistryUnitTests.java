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
package org.springframework.modulith.events.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DefaultEventPublicationRegistry}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class DefaultEventPublicationRegistryUnitTests {

	@Mock EventPublicationRepository repository;

	@Test // GH-206
	void usesCustomClockIfConfigured() {

		when(repository.create(any())).then(returnsFirstArg());

		var now = Instant.now();

		var registry = createRegistry(now);

		var identifier = PublicationTargetIdentifier.of("id");
		var publications = registry.store(new Object(), Stream.of(identifier));

		assertThat(publications).hasSize(1).element(0).satisfies(it -> {
			assertThat(it.getPublicationDate()).isEqualTo(now);
			assertThat(it.getTargetIdentifier()).isEqualTo(identifier);
		});
	}

	@Test // GH-819
	void removesFailingResubmissionFromInProgressPublications() {

		when(repository.create(any())).then(returnsFirstArg());

		var registry = createRegistry(Instant.now());
		var identifier = PublicationTargetIdentifier.of("id");

		var failedPublications = registry.store(new Object(), Stream.of(identifier)).stream()
				.peek(registry::markFailed)
				.toList();

		// Failed completions are not present in the in progress ones
		assertThat(registry.getPublicationsInProgress()).isEmpty();

		when(repository.findIncompletePublications()).thenReturn(failedPublications);

		registry.processIncompletePublications(__ -> true, failingConsumer(), null);

		// Failed re-submissions are not held in the in progress ones, either.
		assertThat(registry.getPublicationsInProgress()).isEmpty();
	}

	@Test // GH-1056
	void obtainsCorrectInProgressPublicationForIdenticalEvents() {

		var inProgress = createRegistry(Instant.now()).getPublicationsInProgress();

		var identifier = PublicationTargetIdentifier.of("id");

		var firstEvent = new SampleEvent("Foo");
		var secondEvent = new SampleEvent("Foo");

		var first = inProgress.register(TargetEventPublication.of(firstEvent, identifier));
		var second = inProgress.register(TargetEventPublication.of(secondEvent, identifier));

		assertThat(inProgress.getPublication(firstEvent, identifier)).containsSame(first);
		assertThat(inProgress.getPublication(secondEvent, identifier)).containsSame(second);
	}

	private DefaultEventPublicationRegistry createRegistry(Instant instant) {

		var clock = Clock.fixed(instant, ZoneId.systemDefault());

		return new DefaultEventPublicationRegistry(repository, clock);
	}

	private Consumer<TargetEventPublication> failingConsumer() {
		return __ -> {
			throw new IllegalStateException();
		};
	}

	record SampleEvent(String payload) {}
}
