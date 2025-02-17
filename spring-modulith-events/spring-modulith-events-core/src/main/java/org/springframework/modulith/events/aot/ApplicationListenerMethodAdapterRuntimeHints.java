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

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.util.ReflectionUtils;

/**
 * @author Oliver Drotbohm
 */
public class ApplicationListenerMethodAdapterRuntimeHints implements RuntimeHintsRegistrar {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.aot.hint.RuntimeHintsRegistrar#registerHints(org.springframework.aot.hint.RuntimeHints, java.lang.ClassLoader)
	 */
	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

		var reflection = hints.reflection();

		var method = ReflectionUtils.findMethod(ApplicationListenerMethodAdapter.class,
				"shouldHandle", ApplicationEvent.class);

		if (method == null) {
			method = ReflectionUtils.findMethod(ApplicationListenerMethodAdapter.class,
					"shouldHandle", ApplicationEvent.class, Object[].class);
		}

		if (method != null) {
			reflection.registerMethod(method, ExecutableMode.INVOKE);
		}
	}
}
