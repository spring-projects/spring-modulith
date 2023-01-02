/*
 * Copyright 2017-2023 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Optional;

/**
 * Default {@link CompletableEventPublication} implementation.
 *
 * @author Oliver Drotbohm
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
@EqualsAndHashCode
@ToString
class DefaultEventPublication implements CompletableEventPublication {

	private final @NonNull Object event;
	private final @NonNull PublicationTargetIdentifier targetIdentifier;
	private final Instant publicationDate = Instant.now();

	private Optional<Instant> completionDate = Optional.empty();

	@Override
	public CompletableEventPublication markCompleted() {

		this.completionDate = Optional.of(Instant.now());
		return this;
	}
}
