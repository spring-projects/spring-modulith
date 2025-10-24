/*
 * Copyright 2025 the original author or authors.
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

import jmh.mbr.junit.Microbenchmark;
import lombok.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry.PublicationsInProgress;

/**
 * @author Oliver Drotbohm
 */
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 10, time = 2)
@Microbenchmark
public class PublicationsInProgressBenchmarks {

	@State(Scope.Benchmark)
	public static class Fixture implements Iterable<TargetEventPublication> {

		private static final int NUMBER = 100;

		private final List<Object> events = new ArrayList<>();
		private final List<PublicationTargetIdentifier> identifiers = new ArrayList<>();
		private final List<TargetEventPublication> randomPublications = new ArrayList<>();

		PublicationsInProgress inProgress;

		public Fixture() {

			this.inProgress = new DefaultEventPublicationRegistry.PublicationsInProgress();

			for (int i = 0; i < NUMBER; i++) {

				var event = new Event(UUID.randomUUID().toString());

				for (int j = 0; j < NUMBER; j++) {

					var identifier = PublicationTargetIdentifier.of(UUID.randomUUID().toString());

					events.add(event);
					identifiers.add(identifier);

					inProgress.register(TargetEventPublication.of(event, identifier));
				}
			}

			var random = new Random();

			for (int i = 0; i < NUMBER; i++) {

				var event = events.get(random.nextInt(NUMBER));
				var id = identifiers.get(random.nextInt(NUMBER));

				randomPublications.add(TargetEventPublication.of(event, id));
			}
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Iterable#iterator()
		 */
		@Override
		public Iterator<TargetEventPublication> iterator() {
			return randomPublications.iterator();
		}

		@Value
		static class Event {
			String value;
		}
	}

	@Benchmark
	public void inProgressPublicationsAccess(Fixture fixture, Blackhole sink) {

		for (TargetEventPublication publication : fixture) {
			sink.consume(fixture.inProgress.getPublication(publication.getEvent(), publication.getTargetIdentifier()));
		}
	}
}
