/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.modulith.events.aot;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.InstanceOfAssertFactories.*;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ExecutableHint;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.event.ApplicationListenerMethodAdapter;

/**
 * Unit tests for {@link ApplicationListenerMethodAdapterRuntimeHints}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationListenerMethodAdapterRuntimeHintsUnitTests {

	@Test // GH-364
	void registersMethodsForReflection() {

		var hints = new RuntimeHints();

		new ApplicationListenerMethodAdapterRuntimeHints()
				.registerHints(hints, getClass().getClassLoader());

		var typeHint = hints.reflection()
				.getTypeHint(ApplicationListenerMethodAdapter.class);

		assertThat(typeHint)
				.isNotNull()
				.extracting(it -> it.methods())
				.asInstanceOf(stream(ExecutableHint.class))
				.hasSize(1)
				.element(0)
				.satisfies(it -> {
					assertThat(it.getName()).isEqualTo("shouldHandle");
					assertThat(it.getMode()).isEqualTo(ExecutableMode.INVOKE);
				});
	}
}
