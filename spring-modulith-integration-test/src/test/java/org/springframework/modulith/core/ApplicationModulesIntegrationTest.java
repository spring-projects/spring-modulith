/*
 * Copyright 2018-2024 the original author or authors.
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

import example.declared.first.First;
import example.declared.fourth.Fourth;
import example.declared.second.Second;
import example.declared.third.Third;
import example.empty.EmptyApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.acme.myproject.Application;
import com.acme.myproject.aot.Some$$SpringCGLIB$$Proxy;
import com.acme.myproject.aot.Spring__Aot;
import com.acme.myproject.complex.internal.FirstTypeBasedPort;
import com.acme.myproject.complex.internal.SecondTypeBasePort;
import com.acme.myproject.moduleA.ServiceComponentA;
import com.acme.myproject.moduleA.SomeConfigurationA.SomeAtBeanComponentA;
import com.acme.myproject.moduleB.ServiceComponentB;
import com.acme.myproject.validator.SampleValidator;

/**
 * @author Oliver Drotbohm
 * @author Peter Gafert
 */
class ApplicationModulesIntegrationTest {

	ApplicationModules modules = ApplicationModules.of(Application.class);

	@Test
	void moduleDetectionUsesStrategyDefinedInSpringFactories() {
		assertThat(TestModuleDetectionStrategy.used).isTrue();
	}

	@Test
	void exposesModulesForPrimaryPackages() {

		assertThat(modules.getModuleByName("moduleB")).hasValueSatisfying(it -> {
			assertThat(it.getBootstrapDependencies(modules)).anySatisfy(dep -> {
				assertThat(dep.getName()).isEqualTo("moduleA");
			});
		});
	}

	@Test
	void usesExplicitlyAnnotatedDisplayName() {

		assertThat(modules.getModuleByName("moduleC")).hasValueSatisfying(it -> {
			assertThat(it.getDisplayName()).isEqualTo("MyModule C");
		});
	}

	@Test
	void rejectsDependencyIntoInternalPackage() {

		Optional<ApplicationModule> module = modules.getModuleByName("invalid");

		assertThat(module).hasValueSatisfying(it -> {
			assertThatExceptionOfType(Violations.class) //
					.isThrownBy(() -> it.verifyDependencies(modules));
		});
	}

	@Test
	void complexModuleExposesNamedInterfaces() {

		assertThat(modules.getModuleByName("complex")).hasValueSatisfying(it -> {

			var interfaces = it.getNamedInterfaces();
			var reference = List.of("API", "SPI", "Port 1", "Port 2", "Port 3");

			assertThat(interfaces).extracting(NamedInterface::getName) //
					.hasSize(reference.size() + 1) //
					.containsAll(reference);

			verifyNamedInterfaces(interfaces, "Port 1", FirstTypeBasedPort.class, SecondTypeBasePort.class);
			verifyNamedInterfaces(interfaces, "Port 2", FirstTypeBasedPort.class, SecondTypeBasePort.class);
			verifyNamedInterfaces(interfaces, "Port 3", FirstTypeBasedPort.class, SecondTypeBasePort.class);
		});
	}

	@Test
	void detectsReferenceToUndeclaredNamedInterface() {

		assertThat(modules.getModuleByName("invalid3")).hasValueSatisfying(it -> {
			assertThatExceptionOfType(Violations.class).isThrownBy(() -> it.verifyDependencies(modules))
					.withMessageContaining("Allowed targets")
					.withMessageContaining("complex :: API");
		});
	}

	@Test
	void discoversAtBeanComponent() {

		Optional<ApplicationModule> module = modules.getModuleByName("moduleA");

		assertThat(module).hasValueSatisfying(it -> {
			assertThat(it.getSpringBeansInternal().contains(SomeAtBeanComponentA.class.getName())).isTrue();
		});
	}

	@Test
	void moduleBListensToModuleA() {

		Optional<ApplicationModule> module = modules.getModuleByName("moduleB");
		ApplicationModule moduleA = modules.getModuleByName("moduleA").orElseThrow(IllegalStateException::new);

		assertThat(module).hasValueSatisfying(it -> {
			assertThat(it.getDependencies(modules, DependencyType.EVENT_LISTENER).contains(moduleA)).isTrue();
		});
	}

	@Test
	void rejectsNotExplicitlyListedDependency() {

		Optional<ApplicationModule> moduleByName = modules.getModuleByName("invalid2");

		assertThat(moduleByName).hasValueSatisfying(it -> {

			assertThatExceptionOfType(Violations.class) //
					.isThrownBy(() -> it.verifyDependencies(modules)) //
					.withMessageContaining(it.getName());
		});
	}

	@Test
	void findsModuleBySubPackage() {

		assertThat(modules.getModuleForPackage("com.acme.myproject.moduleA.sub.package")) //
				.isEqualTo(modules.getModuleByName("moduleA"));
	}

	@Test
	void createsModulesFromJavaPackage() {

		ApplicationModules fromPackage = ApplicationModules.of(Application.class.getPackage().getName());

		assertThat(fromPackage.stream().map(ApplicationModule::getName)) //
				.containsExactlyInAnyOrderElementsOf(
						modules.stream().map(ApplicationModule::getName).toList());
	}

	@Test // #102
	void ordersTypesByModuleDependencies() {

		// Non-module type first, B depending on A
		var source = new ArrayList<>(List.of(String.class, ServiceComponentB.class, ServiceComponentA.class));

		source.sort(ApplicationModules.of(Application.class).getComparator());

		// Expect A before B before non-module type.
		assertThat(source).containsExactly(ServiceComponentA.class, ServiceComponentB.class, String.class);
	}

	@Test // GH-267
	void explicitEmptyAllowedModulesResultsInAllDependenciesRejected() {

		var modules = ApplicationModules.of("example.declared");
		var first = modules.getModuleByType(First.class).orElseThrow();
		var second = modules.getModuleByType(Second.class).orElseThrow();
		var third = modules.getModuleByType(Third.class).orElseThrow();

		// Disallowed due to allowedDependencies = {}
		assertThat(first.getDeclaredDependencies(modules).isAllowedDependency(Second.class)).isFalse();

		// Allowed as allowedDependencies not set
		assertThat(second.getDeclaredDependencies(modules).isAllowedDependency(Third.class)).isTrue();
		assertThat(third.getDeclaredDependencies(modules).isAllowedDependency(Fourth.class)).isTrue();
	}

	@Test // GH-406
	void excludesSpringAOTGeneratedTypes() {

		assertThat(modules.getModuleByName("aot")).hasValueSatisfying(it -> {
			assertThat(it.contains(Spring__Aot.class)).isFalse();
			assertThat(it.contains(Some$$SpringCGLIB$$Proxy.class)).isFalse();
		});
	}

	@Test // GH-418
	void detectsDependencyInducedByValidatorImplementation() {

		assertThat(modules.getModuleByName("validator")).hasValueSatisfying(it -> {

			assertThat(it.getSpringBeans())
					.extracting(SpringBean::getFullyQualifiedTypeName)
					.containsExactly(SampleValidator.class.getName());

			assertThat(it.getBootstrapDependencies(modules).map(ApplicationModule::getName))
					.containsExactly("moduleA");
		});
	}

	@Test // GH-284
	void detectsOpenModule() {

		assertThat(modules.getModuleByName("open")).hasValueSatisfying(it -> {
			assertThat(it.isOpen()).isTrue();
		});

		var detectViolations = modules.detectViolations().getMessages();

		assertThat(detectViolations)
				.isNotEmpty()

				// No invalid references to internals from unrestricted module
				.noneMatch(it -> it.matches("Module 'openclient' depends on non-exposed type .* within module 'open'"))

				// Invalid reference to internals from restricted module
				.anyMatch(it -> it.contains("Module 'opendisallowedclient' depends on module 'open'"))

				// No cycle detection
				.anyMatch(it -> it.contains("Cycle detected: Slice cycleA"))
				.noneMatch(it -> it.contains("Cycle detected: Slice open"));
	}

	@Test // GH-520
	void bootstrapsOnEmptyProject() {

		assertThatNoException().isThrownBy(() -> ApplicationModules.of(EmptyApplication.class).verify());
		assertThatIllegalArgumentException().isThrownBy(() -> ApplicationModules.of("non.existant"));
	}

	private static void verifyNamedInterfaces(NamedInterfaces interfaces, String name, Class<?>... types) {

		Stream.of(types).forEach(type -> {
			assertThat(interfaces.getByName(name)).hasValueSatisfying(named -> named.contains(type));
		});
	}
}
