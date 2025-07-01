/*
 * Copyright 2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.Types.JavaTypes;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Unit tests for {@link Types}.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
class TypesUnitTests {

	@Test // GH-1264
	@SuppressWarnings("null")
	void detectsRelatedClasses() {

		var classes = TestUtils.getClasses(TypesUnitTests.class);
		var relatedClasses = JavaTypes.relatedTypesOf(classes.getRequiredClass(Root.class), __ -> true);

		assertThat(relatedClasses)
				.<Class<?>> extracting(JavaClass::reflect)
				.contains(OnPublicConstructor.class, PublicMethodParameter.class, PublicMethodReturnType.class)
				.doesNotContain(OnProtectedConstructor.class, Object.class, ProtectedMethodParameter.class,
						ProtectedConstructorParameter.class);
	}

	public static class Root {

		public Root(OnPublicConstructor first, ProtectedConstructorParameter second) {}

		Root(OnProtectedConstructor first) {}

		public @Nullable PublicMethodReturnType method(PublicMethodParameter first, ProtectedMethodParameter second) {
			return null;
		}

		public @Nullable Object methodReturningJavaType() {
			return null;
		}
	}

	public static class OnPublicConstructor {}

	public static class OnProtectedConstructor {}

	public static class PublicMethodReturnType {}

	public static class PublicMethodParameter {}

	static class ProtectedMethodParameter {}

	static class ProtectedConstructorParameter {}
}
