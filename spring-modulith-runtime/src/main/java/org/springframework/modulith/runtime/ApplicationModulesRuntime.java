/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.modulith.runtime;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

import org.springframework.modulith.model.ApplicationModules;

/**
 * Bootstrap type to make sure we only bootstrap the initialization of a {@link ApplicationModules} instance once per
 * application class.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
public class ApplicationModulesRuntime implements Supplier<ApplicationModules> {

	private final @NonNull Supplier<ApplicationModules> modules;
	private final @NonNull ApplicationRuntime runtime;

	/*
	 * (non-Javadoc)
	 * @see java.util.function.Supplier#get()
	 */
	@Override
	public ApplicationModules get() {
		return modules.get();
	}

	/**
	 * Returns whether a given {@link Class} is considered an application one (versus Framework ones).
	 *
	 * @param type
	 * @return
	 */
	public boolean isApplicationClass(Class<?> type) {
		return runtime.isApplicationClass(type);
	}

	/**
	 * Returns the actual user class for a given bean and bean name.
	 *
	 * @param bean must not be {@literal null}.
	 * @param beanName must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Class<?> getUserClass(Object bean, String beanName) {
		return runtime.getUserClass(bean, beanName);
	}
}