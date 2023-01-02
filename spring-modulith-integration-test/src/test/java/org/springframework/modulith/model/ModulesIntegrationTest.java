/*
 * Copyright 2018-2023 the original author or authors.
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
package org.springframework.modulith.model;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.acme.myproject.Application;
import com.acme.myproject.complex.internal.FirstTypeBasedPort;
import com.acme.myproject.complex.internal.SecondTypeBasePort;
import com.acme.myproject.moduleA.SomeConfigurationA.SomeAtBeanComponentA;

/**
 * @author Oliver Drotbohm
 * @author Peter Gafert
 */
class ModulesIntegrationTest {

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
					.withMessageContaining("complex::API");
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

	private static void verifyNamedInterfaces(NamedInterfaces interfaces, String name, Class<?>... types) {

		Stream.of(types).forEach(type -> {
			assertThat(interfaces.getByName(name)).hasValueSatisfying(named -> named.contains(type));
		});
	}
}
