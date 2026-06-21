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
 */
@ExtendWith(MockitoExtension.class)
class ActiveModulesUnitTests {

	@Mock BeanFactory beanFactory;
	@Mock Flyway flyway;
	@Mock ModuleTestExecution execution;
	@Mock ObjectProvider<ModuleTestExecution> provider;

	@Test // GH-1750
	void migratesRootModuleWithoutConsultingTestExecution() {

		var filter = new ActiveModules(beanFactory);

		assertThat(filter.shouldMigrate(SpringModulithFlywayMigrationStrategy.ROOT, flyway)).isTrue();
		verifyNoInteractions(beanFactory);
	}

	@Test // GH-1750
	void delegatesToTestExecutionForNonRootModules() {

		var moduleIdentifier = ApplicationModuleIdentifier.of("order");

		when(beanFactory.getBeanProvider(ModuleTestExecution.class)).thenReturn(provider);
		when(provider.getIfAvailable()).thenReturn(execution);
		when(execution.isIncludedInExecution(moduleIdentifier)).thenReturn(true);

		var filter = new ActiveModules(beanFactory);

		assertThat(filter.shouldMigrate(moduleIdentifier, flyway)).isTrue();
		verify(execution).isIncludedInExecution(moduleIdentifier);
	}

	@Test // GH-1750
	void skipsModuleNotIncludedInTestExecution() {

		var moduleIdentifier = ApplicationModuleIdentifier.of("inventory");

		when(beanFactory.getBeanProvider(ModuleTestExecution.class)).thenReturn(provider);
		when(provider.getIfAvailable()).thenReturn(execution);
		when(execution.isIncludedInExecution(moduleIdentifier)).thenReturn(false);

		var filter = new ActiveModules(beanFactory);

		assertThat(filter.shouldMigrate(moduleIdentifier, flyway)).isFalse();
	}

	@Test // GH-1750
	void migratesAllModulesWhenNoTestExecutionAvailable() {

		when(beanFactory.getBeanProvider(ModuleTestExecution.class)).thenReturn(provider);
		when(provider.getIfAvailable()).thenReturn(null);

		var filter = new ActiveModules(beanFactory);

		assertThat(filter.shouldMigrate(ApplicationModuleIdentifier.of("order"), flyway)).isTrue();
		assertThat(filter.shouldMigrate(SpringModulithFlywayMigrationStrategy.ROOT, flyway)).isTrue();
	}
}
