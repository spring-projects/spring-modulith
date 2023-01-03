/*
 * Copyright 2022-2023 the original author or authors.
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
package org.springframework.modulith.test;

import org.assertj.core.api.AssertProvider;

/**
 * An AssertJ-based extension of {@link PublishedEvents} to obtain fluent assertions.
 *
 * @author Oliver Drotbohm
 */
public interface AssertablePublishedEvents extends PublishedEvents, AssertProvider<PublishedEventsAssert> {

	/*
	 * (non-Javadoc)
	 * @see org.assertj.core.api.AssertProvider#assertThat()
	 */
	@Override
	public default PublishedEventsAssert assertThat() {
		return new PublishedEventsAssert(this);
	}
}
