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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.jmolecules.event.annotation.Externalized;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.modulith.events.RoutingTarget.ParsedRoutingTarget;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AnnotationTargetLookup}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class AnnotationTargetLookupUnitTests {

	@TestFactory // GH-248
	Stream<DynamicTest> detectesModulithExternalizedTarget() {

		var tests = Stream.of(
				new $(Unannotated.class, null),
				new $(WithJMoleculesExternalizedValue.class, RoutingTarget.parse("jMoleculesTarget")),
				new $(WithJMoleculesExternalizedTarget.class, RoutingTarget.parse("jMoleculesTarget")),
				new $(WithModulithExternalizedValue.class, RoutingTarget.parse("modulithTarget")),
				new $(WithModulithExternalizedTarget.class, RoutingTarget.parse("modulithTarget")));

		return DynamicTest.stream(tests, $::verify);
	}

	@Test // GH-248
	void cachesLookups() {

		wipeCache();

		AnnotationTargetLookup.of(Unannotated.class);
		assertCacheEntries(1);

		AnnotationTargetLookup.of(WithJMoleculesExternalizedValue.class);
		assertCacheEntries(2);

		AnnotationTargetLookup.of(Unannotated.class);
		assertCacheEntries(2);
	}

	private static void wipeCache() {
		ReflectionTestUtils.setField(AnnotationTargetLookup.class, "LOOKUPS", new HashMap<>());
	}

	private static void assertCacheEntries(int size) {

		Map<?, ?> lookups = (Map<?, ?>) ReflectionTestUtils.getField(AnnotationTargetLookup.class, "LOOKUPS");

		assertThat(lookups).hasSize(size);
	}

	class Unannotated {}

	@Externalized("jMoleculesTarget")
	class WithJMoleculesExternalizedValue {}

	@Externalized(target = "jMoleculesTarget")
	class WithJMoleculesExternalizedTarget {}

	@org.springframework.modulith.events.Externalized("modulithTarget")
	class WithModulithExternalizedValue {}

	@org.springframework.modulith.events.Externalized(target = "modulithTarget")
	class WithModulithExternalizedTarget {}

	record $(Class<?> type, ParsedRoutingTarget target) implements Named<$> {

		/*
		 * (non-Javadoc)
		 * @see org.junit.jupiter.api.Named#getName()
		 */
		@Override
		public String getName() {
			return target == null
					? "%s does not carry target".formatted(type.getSimpleName())
					: "%s targets %s".formatted(type.getSimpleName(), target);
		}

		/*
		 * (non-Javadoc)
		 * @see org.junit.jupiter.api.Named#getPayload()
		 */
		@Override
		public $ getPayload() {
			return this;
		}

		public void verify() {

			var lookup = AnnotationTargetLookup.of(type).get();

			if (target == null) {
				assertThat(lookup).isEmpty();
			} else {
				assertThat(lookup).hasValue(target);
			}
		}

	}
}
