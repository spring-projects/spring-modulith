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
package org.springframework.modulith.core;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.jmolecules.archunit.HexagonalVerificationOptions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the jMolecules architecture verifications executed by {@link VerificationOptions}.
 *
 * @author Oliver Drotbohm
 */
class VerificationOptionsArchitectureUnitTests {

	static final String BASE = "fixtures.hexagonal";

	ApplicationModules modules = TestUtils.of(BASE);

	@Test // GH-193
	void onlyVerifiesArchitectureWithinModulesByDefault() {

		assertThat(violations(VerificationOptions.defaults()))
				.hasSize(1)
				.allSatisfy(it -> assertThat(it).contains("OrderPrimaryAdapter.secondaryAdapter"));
	}

	@Test // GH-193
	void governsReferencesBetweenModulesIfConfigured() {

		var hexagonal = HexagonalVerifications.defaults().withModulesAsHexagons();
		var options = VerificationOptions.defaults().withArchitectureVerifications(hexagonal);

		assertThat(violations(options))
				.anySatisfy(it -> assertThat(it).contains("OrderPrimaryAdapter.secondaryAdapter"))
				.anySatisfy(it -> assertThat(it).contains("OrderPrimaryAdapter.stock"))
				.noneSatisfy(it -> assertThat(it).contains("OrderApplicationService.stock"));
	}

	@Test // GH-193
	void usesJMoleculesDefaultsForExposureIfConfiguredExplicitly() {

		var hexagonal = HexagonalVerifications.of(HexagonalVerificationOptions.defaults()).withModulesAsHexagons();
		var options = VerificationOptions.defaults().withArchitectureVerifications(hexagonal);

		// jMolecules only allows secondary adapters to refer to other modules by default
		assertThat(violations(options))
				.anySatisfy(it -> assertThat(it).contains("OrderApplicationService.stock"));
	}

	@Test // GH-193
	void verifiesReferencesToModulesDeclaredAsHexagonByDefault() {

		var violations = violations(TestUtils.of("fixtures.hexagons"), VerificationOptions.defaults());

		assertThat(violations)
				.anySatisfy(it -> assertThat(it).contains("OrderPrimaryAdapter.stock")
						.contains("which only exposes primary adapters"))
				.noneSatisfy(it -> assertThat(it).contains("OrderApplicationService.stock"))
				.noneSatisfy(it -> assertThat(it).contains("ShippingApplicationService.orders"));
	}

	@Test // GH-193
	void verifiesReferencesToAllModulesIfTreatedAsHexagons() {

		var hexagonal = HexagonalVerifications.defaults().withModulesAsHexagons();
		var options = VerificationOptions.defaults().withArchitectureVerifications(hexagonal);

		assertThat(violations(TestUtils.of("fixtures.hexagons"), options))
				.anySatisfy(it -> assertThat(it).contains("OrderPrimaryAdapter.stock"))
				.anySatisfy(it -> assertThat(it).contains("ShippingApplicationService.orders"))
				.noneSatisfy(it -> assertThat(it).contains("OrderApplicationService.stock"));
	}

	@Test // GH-193
	void verifiesReferencesToModulesDeclaredAsLayeredByDefault() {

		assertThat(violations(TestUtils.of("fixtures.layered"), VerificationOptions.defaults()))
				.anySatisfy(it -> assertThat(it).contains("OrderInfrastructure.customers")
						.contains("which only exposes application layer types and domain layer types"))
				.noneSatisfy(it -> assertThat(it).contains("OrderInfrastructure.service"))
				.noneSatisfy(it -> assertThat(it).contains("OrderController.customers"))
				.noneSatisfy(it -> assertThat(it).contains("OrderService.customers"))
				.noneSatisfy(it -> assertThat(it).contains("InvoiceService.orders"));
	}

	@Test // GH-193
	void verifiesReferencesToAllModulesIfTreatedAsLayered() {

		var layered = LayeredVerifications.defaults().withModulesAsLayered();
		var options = VerificationOptions.defaults().withArchitectureVerifications(layered);

		// Orders now uses layers, so only its application and domain layer types may refer to other modules
		assertThat(violations(TestUtils.of("fixtures.layered"), options))
				.anySatisfy(it -> assertThat(it).contains("InvoiceService.orders"))
				.anySatisfy(it -> assertThat(it).contains("OrderController.customers"))
				.anySatisfy(it -> assertThat(it).contains("OrderInfrastructure.service"))
				.noneSatisfy(it -> assertThat(it).contains("OrderService.customers"));
	}

	private List<String> violations(VerificationOptions options) {
		return violations(modules, options);
	}

	private static List<String> violations(ApplicationModules modules, VerificationOptions options) {

		return modules.detectViolations(options).getMessages().stream()
				.distinct()
				.toList();
	}
}
