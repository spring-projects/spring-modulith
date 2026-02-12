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
package org.springframework.modulith.core;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PackageName}.
 *
 * @author Oliver Drotbohm
 */
class PackageNameUnitTests {

	@Test // GH-578
	void sortsPackagesByNameAndDepth() {

		var comAcme = PackageName.of("com.acme");
		var comAcmeA = PackageName.of("com.acme.a");
		var comAcmeAFirst = PackageName.of("com.acme.a.first");
		var comAcmeAFirstOne = PackageName.of("com.acme.a.first.one");
		var comAcmeASecond = PackageName.of("com.acme.a.second");
		var comAcmeB = PackageName.of("com.acme.b");

		assertThat(List.of(comAcmeAFirstOne, comAcmeB, comAcmeASecond, comAcmeAFirst, comAcme, comAcmeA)
				.stream()
				.sorted((l, r) -> -l.compareTo(r))
				.map(it -> it.getLocalName("com")))
						.containsExactly("acme.b", "acme.a.second", "acme.a.first.one", "acme.a.first", "acme.a", "acme");
	}

	@Test // GH-802
	void caculatesNestingCorrectly() {

		var comAcme = PackageName.of("com.acme");
		var comAcmeA = PackageName.of("com.acme.a");

		assertThat(comAcme.contains(comAcme)).isTrue();
		assertThat(comAcme.contains(comAcmeA)).isTrue();
		assertThat(comAcmeA.contains(comAcme)).isFalse();
	}

	@Test
	void findsMatchingSegments() {

		var source = PackageName.of("com.acme.foo");

		assertThat(source.nameContainsOrMatches("acme")).isTrue();
		assertThat(source.nameContainsOrMatches("*me")).isTrue();
		assertThat(source.nameContainsOrMatches("ac*")).isTrue();
		assertThat(source.nameContainsOrMatches("*m.acme.foo")).isTrue();
		assertThat(source.nameContainsOrMatches("*m.acme.?oo")).isTrue();
		assertThat(source.nameContainsOrMatches("*m.ac*")).isTrue();
		assertThat(source.nameContainsOrMatches("*m.*.fo*")).isTrue();

		assertThat(source.nameContainsOrMatches("cm")).isFalse();

	}
}
