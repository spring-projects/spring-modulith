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

import example.Example;
import example.ni.AnnotatedNamedInterfaceType;
import example.ni.RootType;
import example.ni.api.ApiType;
import example.ni.internal.AdditionalSpiType;
import example.ni.internal.DefaultedNamedInterfaceType;
import example.ni.internal.Internal;
import example.ni.nested.InNested;
import example.ni.nested.NestedNi;
import example.ni.nested.a.InNestedA;
import example.ni.nested.b.InNestedB;
import example.ni.nested.b.first.InNestedBFirst;
import example.ni.nested.b.second.InNestedBSecond;
import example.ni.ontype.Exposed;
import example.ni.propagate.AnotherRelatedType;
import example.ni.propagate.RelatedType;
import example.ni.propagate.TransitivelyRelated;
import example.ni.spi.SpiType;
import example.ninvalid.InvalidDefaultNamedInterface;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Unit tests for {@link NamedInterfaces}.
 *
 * @author Oliver Drotbohm
 */
class NamedInterfacesUnitTests {

	@Test
	void discoversNamedInterfaces() {

		var javaPackage = TestUtils.getPackage(RootType.class)
				.without(new JavaPackages(TestUtils.getPackage(NestedNi.class)));
		var interfaces = NamedInterfaces.discoverNamedInterfaces(javaPackage);

		assertThat(interfaces).map(NamedInterface::getName)
				.containsExactlyInAnyOrder(NamedInterface.UNNAMED_NAME, "api", "spi", "kpi", "internal", "ontype", "propagate");

		assertInterfaceContains(interfaces, NamedInterface.UNNAMED_NAME, RootType.class);
		assertInterfaceContains(interfaces, "api", ApiType.class, AnnotatedNamedInterfaceType.class);
		assertInterfaceContains(interfaces, "spi", SpiType.class, AdditionalSpiType.class);
		assertInterfaceContains(interfaces, "kpi", AdditionalSpiType.class);
		assertInterfaceContains(interfaces, "internal", DefaultedNamedInterfaceType.class);
		assertInterfaceContains(interfaces, "ontype", Exposed.class);
	}

	@Test // GH-183
	void rejectsDefaultingNamedInterfaceTypeInBasePackage() {

		var javaPackage = TestUtils.getPackage(InvalidDefaultNamedInterface.class);

		assertThatIllegalStateException()
				.isThrownBy(() -> NamedInterfaces.discoverNamedInterfaces(javaPackage))
				.withMessageContaining("named interface defaulting")
				.withMessageContaining(InvalidDefaultNamedInterface.class.getSimpleName());
	}

	@Test // GH-284
	void detectsOpenNamedInterface() {

		var javaPackage = TestUtils.getPackage(RootType.class);
		var interfaces = NamedInterfaces.forOpen(javaPackage);

		assertThat(interfaces).map(NamedInterface::getName)
				.containsExactlyInAnyOrder(NamedInterface.UNNAMED_NAME, "api", "spi", "kpi", "internal", "nestedNi", "ontype",
						"propagate");

		assertInterfaceContains(interfaces, NamedInterface.UNNAMED_NAME,
				RootType.class, Internal.class, InNested.class, InNestedA.class, InNestedB.class, InNestedBFirst.class,
				InNestedBSecond.class, AnotherRelatedType.class, RelatedType.class, TransitivelyRelated.class);
	}

	@Test // GH-595
	void detectsNamedInterfacesATypeIsContainedIn() {

		var javaPackage = TestUtils.getPackage(RootType.class);
		var interfaces = NamedInterfaces.discoverNamedInterfaces(javaPackage);

		assertThat(interfaces.getNamedInterfacesContaining(AdditionalSpiType.class))
				.extracting(NamedInterface::getName)
				.containsExactlyInAnyOrder("spi", "kpi");
	}

	@Test
	void createsNamedInterfacesFromBuilder() {

		JavaPackage pkg = TestUtils.getPackage(RootType.class);

		var result = NamedInterfaces.builder(pkg)
				.excluding("internal")
				.matching("nested")
				.build();

		assertThat(result).hasSize(2)
				.extracting(NamedInterface::getName)
				.containsExactlyInAnyOrder(NamedInterface.UNNAMED_NAME, "nested");
	}

	@Test
	void createsNamedInterfacesFromRecursiveBuilder() {

		JavaPackage pkg = TestUtils.getPackage(RootType.class);

		var result = NamedInterfaces.builder(pkg)
				.excluding("internal")
				.matching("nested", "internal")
				.recursive()
				.build();

		assertThat(result).hasSize(6)
				.extracting(NamedInterface::getName)
				.containsExactlyInAnyOrder(NamedInterface.UNNAMED_NAME, "nested", "nested.a", "nested.b", "nested.b.first",
						"nested.b.second");
	}

	@Test // GH-1040
	void doesNotExcludeAnyPackagesByDefault() {

		var pkg = TestUtils.getPackage(RootType.class);

		var interfaces = NamedInterfaces.builder(pkg)
				.including(__ -> true)
				.build();

		assertThat(interfaces).hasSizeGreaterThan(1);
	}

	@Test // GH-1139
	void discoveredNamedInterfaceOnComposedAnnotation() {

		var pkg = TestUtils.getPackage(example.metani.Exposed.class);

		var result = NamedInterfaces.discoverNamedInterfaces(pkg);

		assertThat(result).hasSize(2)
				.extracting(NamedInterface::getName)
				.containsExactlyInAnyOrder(NamedInterface.UNNAMED_NAME, "api");
	}

	@Test // GH-1264
	@SuppressWarnings("null")
	void propagatesNamedInterfaceAssignment() {

		var pkg = TestUtils.getPackage(RootType.class);
		var result = NamedInterfaces.discoverNamedInterfaces(pkg);

		assertThat(result.getByName("propagate")).hasValueSatisfying(it -> {

			assertThat(it)
					.<Class<?>> extracting(JavaClass::reflect)
					.contains(RelatedType.class, AnotherRelatedType.class)
					.doesNotContain(Example.class);
		});
	}

	private static void assertInterfaceContains(NamedInterfaces interfaces, String name, Class<?>... types) {

		var classNames = Arrays.stream(types).map(Class::getName).toArray(String[]::new);

		assertThat(interfaces.getByName(name)).hasValueSatisfying(it -> {
			assertThat(it).map(JavaClass::getName).containsExactlyInAnyOrder(classNames);
		});
	}
}
