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
package org.springframework.modulith.events;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RoutingTarget}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class RoutingTargetUnitTests {

	@Test // GH-248
	void targetWithoutDelimiterDoesNotExposeKey() {

		var target = RoutingTarget.parse("target");

		assertThat(target.getTarget()).isEqualTo("target");
		assertThat(target.getKey()).isNull();
	}

	@Test // GH-248
	void presenceOfSoleDelimiterResultsInEmptyKey() {

		assertThat(RoutingTarget.parse("target::key").getKey()).isEqualTo("key");
		assertThat(RoutingTarget.parse("target::").getKey()).isEqualTo("");
	}

	@Test // GH-248
	void detectsKeyExpression() {

		assertThat(RoutingTarget.forTarget("target").andKey("key").hasKeyExpression()).isFalse();
		assertThat(RoutingTarget.forTarget("target").andKey("#{expression}").hasKeyExpression()).isTrue();
	}

	@Test // GH-248
	void createsEmptyParsedRoutingTarget() {

		var target = RoutingTarget.parse("");

		assertThat(target.getTarget()).isNull();
		assertThat(target.getKey()).isNull();
		assertThatIllegalStateException().isThrownBy(target::toRoutingTarget);
	}

	@Test // GH-248
	void equalsAndHashCode() {

		var first = RoutingTarget.parse("target");
		var second = RoutingTarget.parse("target::");
		var third = RoutingTarget.parse("target::key");
		var fourth = RoutingTarget.parse("target::key");
		var fifth = RoutingTarget.parse("another");

		assertThat(first).isEqualTo(first);
		assertThat(first).isNotEqualTo(second);
		assertThat(first).isNotEqualTo(third);
		assertThat(first).isNotEqualTo(fifth);
		assertThat(first).isNotEqualTo(new Object());

		assertThat(third).isEqualTo(fourth);
		assertThat(fourth).isEqualTo(third);

		// Unfortunate but not to avoid
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first.hashCode()).isNotEqualTo(third);
	}

	@Test // GH-881
	void trimsTargetAndKeyOnParsing() {

		var target = RoutingTarget.parse(" target :: key ");

		assertThat(target.getTarget()).isEqualTo("target");
		assertThat(target.getKey()).isEqualTo("key");
	}

	@Test // GH-881
	void trimsTargetAndKeyOnBuilding() {

		var target = RoutingTarget.forTarget(" target ").andKey(" key ");

		assertThat(target.getTarget()).isEqualTo("target");
		assertThat(target.getKey()).isEqualTo("key");
	}
}
