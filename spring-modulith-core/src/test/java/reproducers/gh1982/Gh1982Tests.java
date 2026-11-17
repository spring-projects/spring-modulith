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
package reproducers.gh1982;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.core.JavaPackage;
import org.springframework.modulith.core.TestUtils;

class Gh1982Tests {

	@Test // GH-1982
	void discoversOverlappingAdditionalPackagesWithoutSplittingModules() {

		var modules = TestUtils.of(WithOverlappingAdditionalPackages.class);

		assertThat(modules.stream()
				.map(it -> it.getBasePackage().getName())
				.toList())
				.containsExactlyInAnyOrder(
						"reproducers.gh1982fixtures.billing",
						"reproducers.gh1982fixtures.billing.invoice");

		assertThat(modules.getRootPackages())
				.extracting(JavaPackage::getName)
				.containsExactly(
						"reproducers.gh1982",
						"reproducers.gh1982fixtures");
	}

	@Test // GH-1982
	void doesNotSplitModulesWithFullyQualifiedNames() {

		var modules = TestUtils.of(WithFullyQualifiedModuleNames.class);

		assertThat(modules.stream()
				.map(it -> it.getIdentifier().toString())
				.toList())
				.containsExactlyInAnyOrder(
						"reproducers.gh1982fixtures.billing",
						"reproducers.gh1982fixtures.billing.invoice");
	}

	@Test // GH-1982
	void rejectsEnclosingPackagesDuringModuleDiscovery() {

		assertThatIllegalArgumentException()
				.isThrownBy(() -> TestUtils.of(WithEnclosingAdditionalPackage.class))
				.withMessageContaining("must not enclose")
				.withMessageContaining("reproducers")
				.withMessageContaining("reproducers.gh1982");
	}

	@Modulithic(additionalPackages = {
			"reproducers.gh1982fixtures",
			"reproducers.gh1982fixtures.billing"
	})
	static class WithOverlappingAdditionalPackages {}

	@Modulithic(
			additionalPackages = {
					"reproducers.gh1982fixtures",
					"reproducers.gh1982fixtures.billing"
			},
			useFullyQualifiedModuleNames = true
	)
	static class WithFullyQualifiedModuleNames {}

	@Modulithic(additionalPackages = "reproducers")
	static class WithEnclosingAdditionalPackage {}
}
