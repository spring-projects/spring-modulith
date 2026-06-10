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
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.util.Assert;

/**
 * A filter to decide which Flyway migrations to execute.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
public interface MigrationFilter {

	public static final MigrationFilter USE_ALL = (identifier, flyway) -> true;

	/**
	 * Whether to execute the migration for the given {@link ApplicationModuleIdentifier}.
	 *
	 * @param identifier will never be {@literal null}.
	 * @param flyway will never be {@literal null}.
	 */
	boolean shouldMigrate(ApplicationModuleIdentifier identifier, Flyway flyway);

	/**
	 * Creates a new {@link MigrationFilter} that applies the current filter and the given one.
	 *
	 * @param that must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	default MigrationFilter and(MigrationFilter that) {

		Assert.notNull(that, "MigrationFilter must not be null!");

		return (identifier, flyway) -> this.shouldMigrate(identifier, flyway) && that.shouldMigrate(identifier, flyway);
	}
}
