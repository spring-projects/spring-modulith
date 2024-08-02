/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.modulith.events.jdbc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StopWatch;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Not an actual test but can be executed in case of suspected regressions.
 *
 * @author Oliver Drotbohm
 */
@JdbcTest(properties = "spring.modulith.events.jdbc.schema-initialization.enabled=true")
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(classes = { TestApplication.class, JdbcEventPublicationAutoConfiguration.class })
@ActiveProfiles("postgres")
class JdbcEventPublicationRepositoryPerformance {

	@Autowired JdbcOperations operations;
	@Autowired EventPublicationRepository repository;
	@MockitoBean EventSerializer serializer;

	TargetEventPublication toComplete;
	Instant now = Instant.now();
	List<TargetEventPublication> publications;

	int number = 10000;
	int fractionCompleted = 95;
	String serializedEvent = "{\"eventId\":\"id\"}";

	@BeforeEach
	void setUp() {

		operations.update("DELETE FROM event_publication");

		when(serializer.serialize(any())).thenReturn(serializedEvent);

		publications = IntStream.range(0, number)
				.mapToObj(it -> TargetEventPublication.of(new SampleEvent(),
						PublicationTargetIdentifier.of(UUID.randomUUID().toString())))
				.map(repository::create)
				.toList();

		new Random()
				.ints(number / 100 * fractionCompleted, 0, number)
				.mapToObj(publications::get)
				.forEach(it -> repository.markCompleted(it, Instant.now()));

		do {

			var candidate = publications.get(new Random().nextInt(number));

			if (!candidate.isPublicationCompleted()) {
				toComplete = candidate;
			}

		} while (toComplete == null);
	}

	@Test
	void marksPublicationAsCompletedById() {

		runWithMeasurement("By id", () -> repository.markCompleted(toComplete.getIdentifier(), now));
	}

	@Test
	void marksPublicationAsCompleted() {

		runWithMeasurement("By event and target identifier",
				() -> repository.markCompleted(toComplete.getEvent(), toComplete.getTargetIdentifier(), now));
	}

	void runWithMeasurement(String prefix, Runnable runnable) {

		var watch = new StopWatch();
		watch.start();

		runnable.run();

		watch.stop();

		System.out.println(prefix + " took: " + watch.lastTaskInfo().getTime(TimeUnit.MILLISECONDS) + "ms");
	}

	static class SampleEvent {};
}
