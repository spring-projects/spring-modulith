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
package org.springframework.modulith.runtime.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.test.ModuleTestExecution;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link MigrationFilter} that inspects a {@link ModuleTestExecution} for which modules are actually active and
 * signals that only migrations for those are supposed to be executed.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
public class ActiveModules implements MigrationFilter {

	private static final boolean IN_TEST = ClassUtils.isPresent("org.springframework.modulith.test.ModuleTestExecution",
			SpringModulithFlywayMigrationStrategy.class.getClassLoader());

	private final BeanFactory factory;

	/**
	 * Creates a new {@link ActiveModules} instance for the given {@link BeanFactory}
	 *
	 * @param factory must not be {@literal null}.
	 */
	public ActiveModules(BeanFactory factory) {

		Assert.notNull(factory, "BeanFactory must not be null!");

		this.factory = factory;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.runtime.flyway.MigrationFilter#shouldMigrate(org.springframework.modulith.core.ApplicationModuleIdentifier)
	 */
	@Override
	public boolean shouldMigrate(ApplicationModuleIdentifier identifier, Flyway flyway) {

		if (!IN_TEST) {
			return true;
		}

		var execution = factory.getBeanProvider(ModuleTestExecution.class).getIfAvailable();

		return execution != null ? execution.isIncludedInExecution(identifier) : true;
	}
}
