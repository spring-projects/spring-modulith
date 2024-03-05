/*
 * Copyright 2024 the original author or authors.
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

/**
 * Factory interface to create {@link ApplicationModules} instances for application classes. The default version will
 * simply delegate to {@link ApplicationModules#of(Class)} which will only look at production classes. Our test support
 * provides an alternative implementation to bootstrap an {@link ApplicationModules} instance from test types as well,
 * primarily for our very own integration test purposes.
 *
 * @author Oliver Drotbohm
 * @since 1.2
 */
public interface ApplicationModulesFactory {

	/**
	 * Returns the {@link ApplicationModules} instance for the given application class.
	 *
	 * @param applicationClass must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	ApplicationModules of(Class<?> applicationClass);

	/**
	 * Creates the default {@link ApplicationModulesFactory} delegating to {@link ApplicationModules#of(Class)}
	 *
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModulesFactory defaultFactory() {
		return ApplicationModules::of;
	}
}
