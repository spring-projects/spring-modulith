/*
 * Copyright 2019-2024 the original author or authors.
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

import example.ni.api.ApiType;
import example.ni.spi.SpiType;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.modulith.core.ApplicationModule.DeclaredDependency;

import com.acme.withatbean.SampleAggregate;
import com.acme.withatbean.TestEvents.JMoleculesAnnotated;
import com.acme.withatbean.TestEvents.JMoleculesImplementing;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Unit tests for {@link ApplicationModule}.
 *
 * @author Oliver Drotbohm
 */
@TestInstance(Lifecycle.PER_CLASS)
class ModuleUnitTest {

	String packageName = "com.acme.withatbean";
	ApplicationModule module = TestUtils.getApplicationModule(packageName);

	@Test
	public void considersExternalSpringBeans() {

		assertThat(module.getSpringBeans()) //
				.flatExtracting(SpringBean::getFullyQualifiedTypeName) //
				.contains(DataSource.class.getName());
	}

	@Test
	void discoversPublishedEvents() {

		var classes = module.getClasses();

		JavaClass jMoleculesAnnotated = classes.getRequiredClass(JMoleculesAnnotated.class);
		JavaClass jMoleculesImplementing = classes.getRequiredClass(JMoleculesImplementing.class);

		List<EventType> events = module.getPublishedEvents();

		assertThat(events.stream().map(EventType::getType)) //
				.containsExactlyInAnyOrder(jMoleculesAnnotated, jMoleculesImplementing);
		assertThat(events.stream().filter(it -> it.getType().equals(jMoleculesAnnotated))) //
				.element(0) //
				.satisfies(it -> {
					assertThat(it.getSources()).isNotEmpty();
				});
	}

	@Test // GH-87
	void usesCapitalizedNameAsDisplayNameByDefault() {
		assertThat(module.getDisplayName()).isEqualTo("Withatbean");
	}

	@Test // GH-157
	void detectsAggregates() {

		assertThat(module.getAggregateRoots())
				.<Class<?>> extracting(JavaClass::reflect)
				.containsExactly(SampleAggregate.class);
	}

	@Test // GH-319
	void containsPackage() {

		assertThat(module.containsPackage(packageName)).isTrue();
		assertThat(module.containsPackage(packageName + ".foo")).isTrue();

		assertThat(module.containsPackage(packageName + "foo")).isFalse();
	}

	@Test // GH-601
	void wildcardedDeclaredDependencyAllowsDependenciesToAllNamedInterfaces() {

		var modules = TestUtils.of("example", "example.ninvalid");

		var module = modules.getModuleByName("ni").orElseThrow();
		var dependency = DeclaredDependency.of("ni :: *", module, modules);

		assertThat(dependency.contains(SpiType.class)).isTrue();
		assertThat(dependency.contains(ApiType.class)).isTrue();
	}
}
