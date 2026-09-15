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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.AbandonPolicy.Decision;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.ResubmissionOptions;

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

	@Test // GH-1650
	void processFailedPublicationsCapsReadLimitByMaxInFlightHeadroomNotBatchSize() {

		when(repository.countByStatus(Status.RESUBMITTED)).thenReturn(100);
		when(repository.findFailedPublications(any())).thenReturn(Collections.emptyList());

		var registry = createRegistry(Instant.now());

		registry.processFailedPublications(
				ResubmissionOptions.defaults().withMaxInFlight(Integer.MAX_VALUE).withBatchSize(100), __ -> {});

		verify(repository).findFailedPublications(argThat(criteria -> criteria.getMaxItemsToRead() == 100));
	}

	@Test // GH-1650
	void processFailedPublicationsUsesRemainingInFlightWhenLessThanBatchSize() {

		when(repository.countByStatus(Status.RESUBMITTED)).thenReturn(100);
		when(repository.findFailedPublications(any())).thenReturn(Collections.emptyList());

		var registry = createRegistry(Instant.now());

		registry.processFailedPublications(
				ResubmissionOptions.defaults().withMaxInFlight(150).withBatchSize(100), __ -> {});

		verify(repository).findFailedPublications(argThat(criteria -> criteria.getMaxItemsToRead() == 50));
	}

	@Test // GH-1764
	void marksPublicationAsAbandonedWhenAbandonPolicyApplies() {

		when(repository.create(any())).then(returnsFirstArg());

		var registry = createRegistry(Instant.now(), publication -> true);
		var identifier = PublicationTargetIdentifier.of("id");
		var event = new Object();

		registry.store(event, Stream.of(identifier));
		registry.markFailed(event, identifier);

		verify(repository).markAbandoned(any(), any(), isNull());
		verify(repository, never()).markFailed(any());
	}

	@Test // GH-1764
	void marksPublicationAsFailedWhenAbandonPolicyDoesNotApply() {

		when(repository.create(any())).then(returnsFirstArg());

		var registry = createRegistry(Instant.now());
		var identifier = PublicationTargetIdentifier.of("id");
		var event = new Object();

		registry.store(event, Stream.of(identifier));
		registry.markFailed(event, identifier);

		verify(repository).markFailed(any());
		verify(repository, never()).markAbandoned(any(), any(), any());
	}

	@Test // GH-1764
	void appliesConfiguredPoliciesWhenNoOverrideGiven() {

		var identifier = UUID.randomUUID();
		var publication = mock(TargetEventPublication.class);
		when(publication.getIdentifier()).thenReturn(identifier);

		when(repository.findByStatus(Status.FAILED)).thenReturn(List.of(publication));
		when(repository.markAbandoned(eq(identifier), any(), eq(Status.FAILED))).thenReturn(true);

		var registry = createRegistry(Instant.now(), it -> true);

		registry.applyAbandonPolicy(null);

		verify(repository).markAbandoned(eq(identifier), any(), eq(Status.FAILED));
	}

	@Test // GH-1764
	void doesNotAbandonFailedPublicationsWhenConfiguredPoliciesRetain() {

		var publication = mock(TargetEventPublication.class);

		when(repository.findByStatus(Status.FAILED)).thenReturn(List.of(publication));

		var registry = createRegistry(Instant.now());

		registry.applyAbandonPolicy(null);

		verify(repository, never()).markAbandoned(any(), any(), any());
	}

	@Test // GH-1764
	void appliedOverrideTakesPrecedenceOverConfiguredPolicies() {

		var identifier = UUID.randomUUID();
		var publication = mock(TargetEventPublication.class);
		when(publication.getIdentifier()).thenReturn(identifier);

		when(repository.findByStatus(Status.FAILED)).thenReturn(List.of(publication));
		when(repository.markAbandoned(eq(identifier), any(), eq(Status.FAILED))).thenReturn(true);

		// Configured policies (none) would never abandon anything.
		var registry = createRegistry(Instant.now());

		org.springframework.modulith.events.AbandonPolicy override = __ -> Decision.ABANDON;

		registry.applyAbandonPolicy(override);

		verify(repository).markAbandoned(eq(identifier), any(), eq(Status.FAILED));
	}

	@Test // GH-1836
	void doesNotConsiderRecentlyResubmittedPublicationStaleBasedOnOriginalPublicationDate() {

		var now = Instant.now();

		var publication = mock(TargetEventPublication.class, CALLS_REAL_METHODS);
		lenient().when(publication.getPublicationDate()).thenReturn(now.minus(Duration.ofDays(1)));
		when(publication.getLastResubmissionDate()).thenReturn(now.minus(Duration.ofSeconds(1)));

		when(repository.findByStatus(Status.PUBLISHED)).thenReturn(Collections.emptyList());
		when(repository.findByStatus(Status.PROCESSING)).thenReturn(Collections.emptyList());
		when(repository.findByStatus(Status.RESUBMITTED)).thenReturn(List.of(publication));

		var registry = createRegistry(now);

		registry.markStalePublicationsFailed(status -> Duration.ofMinutes(10));

		// Resubmitted a second ago, well within the 10 minute staleness window, must not be marked failed
		// even though the original publication date is a day old.
		verify(repository, never()).markFailed(any());
	}

	private DefaultEventPublicationRegistry createRegistry(Instant instant) {

		var clock = Clock.fixed(instant, ZoneId.systemDefault());

		return new DefaultEventPublicationRegistry(repository, clock, AbandonPolicies.none());
	}

	private DefaultEventPublicationRegistry createRegistry(Instant instant, Predicate<EventPublication> abandon) {

		var clock = Clock.fixed(instant, ZoneId.systemDefault());
		var policy = (org.springframework.modulith.events.AbandonPolicy) publication -> abandon.test(publication)
				? Decision.ABANDON
				: Decision.RETAIN;

		var abandonPolicies = new AbandonPolicies(List.of(policy), __ -> false);

		return new DefaultEventPublicationRegistry(repository, clock, abandonPolicies);
	}

	private Consumer<TargetEventPublication> failingConsumer() {
		return __ -> {
			throw new IllegalStateException();
		};
	}

	record SampleEvent(String payload) {}
}
