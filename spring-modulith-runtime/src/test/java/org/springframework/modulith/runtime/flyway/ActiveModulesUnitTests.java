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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.test.ModuleTestExecution;

/**
 * Unit tests for {@link ActiveModules}.
 *
 * @author Seonwoo Jung
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class ActiveModulesUnitTests {

	private static final ApplicationModuleIdentifier MODULE_A = ApplicationModuleIdentifier.of("moduleA");
	private static final ApplicationModuleIdentifier MODULE_B = ApplicationModuleIdentifier.of("moduleB");
	private static final ApplicationModuleIdentifier ROOT_MODULE = SpringModulithFlywayMigrationStrategy.ROOT;

	@Mock Flyway flyway;

	@Test // GH-1750
	void executesMigrationsForModulesPartOfTheTestRun() {

		var filter = withModuleExecutions(MODULE_A);

		assertThat(filter.shouldMigrate(ROOT_MODULE, flyway)).isTrue();
		assertThat(filter.shouldMigrate(MODULE_A, flyway)).isTrue();

		assertThat(filter.shouldMigrate(MODULE_B, flyway)).isFalse();
	}

	@Test // GH-1750
	void migratesAllModuleIfNoExecutionRegistered() {

		var filter = withoutModuleExecutions();

		assertThat(filter.shouldMigrate(ROOT_MODULE, flyway)).isTrue();
		assertThat(filter.shouldMigrate(MODULE_A, flyway)).isTrue();
	}

	private static ActiveModules withoutModuleExecutions() {
		return withModuleExecutions((ModuleTestExecution) null);
	}

	private static ActiveModules withModuleExecutions(ApplicationModuleIdentifier... identifiers) {
		return withModuleExecutions(mock(ModuleTestExecution.class), identifiers);
	}

	@SuppressWarnings("unchecked")
	private static final ActiveModules withModuleExecutions(@Nullable ModuleTestExecution execution,
			ApplicationModuleIdentifier... identifiers) {

		var beanFactory = mock(BeanFactory.class);
		ObjectProvider<ModuleTestExecution> provider = mock(ObjectProvider.class);
		when(beanFactory.getBeanProvider(ModuleTestExecution.class)).thenReturn(provider);

		if (execution != null) {

			when(provider.getIfAvailable()).thenReturn(execution);

			for (var identifier : identifiers) {
				when(execution.isIncludedInExecution(identifier)).thenReturn(true);
			}

		} else {
			when(provider.getIfAvailable()).thenReturn(null);
		}

		return new ActiveModules(beanFactory);
	}
}
