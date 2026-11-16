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

import example.Example;
import fixtures.hexagons.inventory.StockPrimaryPort;
import fixtures.hexagons.orders.OrderPrimaryAdapter;

import org.jmolecules.architecture.hexagonal.Hexagon;
import org.jmolecules.archunit.ArchitecturalUnits.ArchitecturalUnit;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Unit tests for {@link ModuleArchitecturalUnitResolver}.
 *
 * @author Oliver Drotbohm
 */
class ModuleArchitecturalUnitResolverUnitTests {

	ApplicationModules modules = TestUtils.of("example", "example.ninvalid", "example.cycle..");
	ModuleArchitecturalUnitResolver resolver = ModuleArchitecturalUnitResolver.of(modules);

	@Test // GH-193
	void resolvesModuleAsUnit() {

		var unit = resolver.getUnitFor(typeIn("ni", "example.ni"));

		assertThat(unit).isNotNull();
		assertThat(unit.getId()).isEqualTo("ni");
		assertThat(unit.getName()).isEqualTo("Ni");
		assertThat(unit.getParent()).isNull();
	}

	@Test // GH-193
	void resolvesNearestUnitForNestedModules() {

		var inner = resolver.getUnitFor(typeIn("ni.nested.b.first", "example.ni.nested.b.first"));
		var nested = resolver.getUnitFor(typeIn("ni.nested", "example.ni.nested"));

		assertThat(inner).isNotNull().extracting(ArchitecturalUnit::getId).isEqualTo("ni.nested.b.first");
		assertThat(nested).isNotNull().extracting(ArchitecturalUnit::getId).isEqualTo("ni.nested");
		assertThat(inner.getParent()).isEqualTo(nested);
		assertThat(nested.getParent()).isNotNull().extracting(ArchitecturalUnit::getId).isEqualTo("ni");
	}

	@Test // GH-193
	void returnsEqualUnitsForTypesInSameModule() {

		var first = resolver.getUnitFor(typeIn("ni", "example.ni"));
		var second = resolver.getUnitFor(typeIn("ni", "example.ni.api"));

		assertThat(first).isNotNull().isEqualTo(second);
	}

	@Test // GH-193
	void doesNotResolveUnitForTypesOutsideOfModules() {
		assertThat(resolver.getUnitFor(new ClassFileImporter().importClass(Example.class))).isNull();
	}

	@Test // GH-193
	void onlyResolvesAnnotatedModulesIfRequested() {

		var hexagons = TestUtils.of("fixtures.hexagons");
		var annotated = ModuleArchitecturalUnitResolver.ofAnnotated(hexagons, Hexagon.class.getName());
		var stock = typeOf(hexagons, StockPrimaryPort.class);
		var order = typeOf(hexagons, OrderPrimaryAdapter.class);

		assertThat(annotated.getUnitFor(stock)).isNotNull().extracting(ArchitecturalUnit::getId).isEqualTo("inventory");
		assertThat(annotated.getUnitFor(order)).isNull();
		assertThat(ModuleArchitecturalUnitResolver.of(hexagons).getUnitFor(order))
				.isNotNull().extracting(ArchitecturalUnit::getId).isEqualTo("orders");
	}

	private static JavaClass typeOf(ApplicationModules modules, Class<?> type) {

		return modules.stream()
				.flatMap(it -> it.getBasePackage().stream())
				.filter(it -> it.isEquivalentTo(type))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Type " + type.getName() + " not found!"));
	}

	private JavaClass typeIn(String module, String packageName) {

		return modules.getModuleByName(module).orElseThrow()
				.getBasePackage().stream()
				.filter(it -> it.getPackageName().equals(packageName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No type found in package " + packageName));
	}
}
