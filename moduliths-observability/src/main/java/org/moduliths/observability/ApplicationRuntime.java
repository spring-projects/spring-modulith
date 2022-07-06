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
package org.moduliths.observability;

/**
 * Abstraction of the application runtime environment. Primarily to keep references to Spring Boot out of the core
 * observability implementation.
 *
 * @author Oliver Drotbohm
 */
public interface ApplicationRuntime {

	/**
	 * Returns the identifier of the application.
	 *
	 * @return
	 */
	String getId();

	/**
	 * Returns the primary application class.
	 *
	 * @return
	 */
	Class<?> getMainApplicationClass();

	/**
	 * Obtain the end user class for the given bean and bean name. Necessary to reveal the actual user type from
	 * potentially proxied instances.
	 *
	 * @param bean
	 * @param beanName
	 * @return
	 */
	Class<?> getUserClass(Object bean, String beanName);

	/**
	 * Returns whether the given type is an application class, i.e. user code in one of the application packages.
	 *
	 * @param type
	 * @return
	 * @see #getApplicationPackages()
	 */
	boolean isApplicationClass(Class<?> type);
}
