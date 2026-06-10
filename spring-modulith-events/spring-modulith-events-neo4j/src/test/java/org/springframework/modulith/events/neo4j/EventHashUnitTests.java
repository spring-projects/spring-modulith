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
package org.springframework.modulith.events.neo4j;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EventHash}.
 *
 * @author Oliver Drotbohm
 */
class EventHashUnitTests {

	static final byte[] PAYLOAD = "{\"eventId\":\"id\"}".getBytes();
	static final String SHA_256 = "9e7e28bdcb17a2d2b55acf5f9f0eb97375eb1138872822d50a4d3917633785b5";
	static final String MD5 = "2e145c334be315d45212263158187174";

	@Test // GH-1718
	void writesSha256Hash() {
		assertThat(EventHash.sha256(PAYLOAD)).isEqualTo(SHA_256);
	}

	@Test // GH-1718
	void candidatesContainCurrentAndLegacyHashInThatOrder() {
		assertThat(EventHash.candidates(PAYLOAD)).containsExactly(SHA_256, MD5);
	}

	@Test // GH-1718
	void candidatesAreStableAcrossInvocations() {

		assertThat(EventHash.candidates(PAYLOAD)).isEqualTo(EventHash.candidates(PAYLOAD));
		assertThat(EventHash.sha256(PAYLOAD)).isEqualTo(EventHash.sha256(PAYLOAD));
	}

	@Test // GH-1718
	void rejectsNullBytes() {

		assertThatIllegalArgumentException().isThrownBy(() -> EventHash.sha256(null));
		assertThatIllegalArgumentException().isThrownBy(() -> EventHash.candidates(null));
	}
}
