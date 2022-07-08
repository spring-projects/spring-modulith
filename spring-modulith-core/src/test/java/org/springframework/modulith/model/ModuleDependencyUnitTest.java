/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.model;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.model.Module.ModuleDependency;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Unit tests for {@link ModuleDependency}.
 *
 * @author Oliver Drotbohm
 */
class ModuleDependencyUnitTest {

	ClassFileImporter importer = new ClassFileImporter();

	@Test
	public void detectsInjectionDependencies() {

		assertThat(findDependencies(SubType.class)) //
				.containsExactlyInAnyOrder(A.class, B.class, C.class, D.class, E.class, F.class);
	}

	@Test
	public void detectsDependencyFromAnnotatedConstructor() {

		assertThat(findDependencies(MultipleConstructors.class)) //
				.containsExactlyInAnyOrder(B.class);
	}

	@Test
	public void detectsDependencyFromSingleUnannotatedConstructor() {

		assertThat(findDependencies(SingleConstructor.class)) //
				.containsExactlyInAnyOrder(B.class);
	}

	private Stream<Class<?>> findDependencies(Class<?> type) {

		return ModuleDependency.fromType(importer.importClass(type)) //
				.map(ModuleDependency::getTarget) //
				.map(JavaClass::reflect);
	}

	static class A {}

	static class B {}

	static class C {}

	static class D {}

	static class E {}

	static class F {}

	static class SomeComponent {

		@Autowired A a;

		@Autowired
		void setD(D d) {}
	}

	static class SubType extends SomeComponent {

		@Autowired E e;

		SubType(B b, C c) {}

		@Autowired
		void setF(F f) {}
	}

	static class MultipleConstructors {

		MultipleConstructors(A a) {}

		@Autowired
		MultipleConstructors(B b) {}
	}

	static class SingleConstructor {
		SingleConstructor(B b) {}
	}
}
