/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.events.jdbc;

import java.util.Collections;
import java.util.Set;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;
import org.springframework.core.Ordered;

/**
 * A {@link org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector} that makes sure the Spring
 * Modulith schema initialization runs after Boot's default file-based schema initialization.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Victoria Canal - Blackbird - https://www.youtube.com/watch?v=qSD4HCLUZOo
 */
class ModulithDatabaseInitializerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector#getDatabaseInitializerBeanTypes()
	 */
	@Override
	protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
		return Collections.singleton(DatabaseSchemaInitializer.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector#getOrder()
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 50;
	}
}
