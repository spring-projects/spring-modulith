/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.modulith.events;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
	@Mock Clock clock;

	@Test // GH-206
	void usesCustomClockIfConfigured() {

		when(repository.create(any())).then(returnsFirstArg());

		var now = Instant.now();
		var clock = Clock.fixed(now, ZoneId.systemDefault());

		var registry = new DefaultEventPublicationRegistry(repository, clock);

		var identifier = PublicationTargetIdentifier.of("id");
		var publications = registry.store(new Object(), Stream.of(identifier));

		assertThat(publications).hasSize(1).element(0).satisfies(it -> {
			assertThat(it.getPublicationDate()).isEqualTo(now);
			assertThat(it.getTargetIdentifier()).isEqualTo(identifier);
		});
	}
}
