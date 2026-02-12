/*
 * Copyright 2022-2026 the original author or authors.
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
package org.springframework.modulith.events.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import example.ExampleApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.jpa.archiving.ArchivedJpaEventPublication;
import org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * @author Oliver Drotbohm
 */
class JpaEventPublicationAutoConfigurationIntegrationTests {

	String examplePackage = ExampleApplication.class.getPackageName();
	String eventPublicationPackage = DefaultJpaEventPublication.class.getPackageName();
	String archivingPackage = ArchivedJpaEventPublication.class.getPackageName();

	@Test // GH-10
	void registersJpaEventPublicationPackageForAutoConfiguration() {
		assertAutoConfigurationPackages(null, examplePackage, eventPublicationPackage);
	}

	@Test // GH-964
	void registersArchivingJpaEventPublicationPackageForAutoConfiguration() {
		assertAutoConfigurationPackages("ARCHIVE", examplePackage, eventPublicationPackage, archivingPackage);
	}

	private void assertAutoConfigurationPackages(String propertyValue, String... packages) {

		var runner = new ApplicationContextRunner();

		if (propertyValue != null) {
			runner = runner.withPropertyValues(CompletionMode.PROPERTY + "=" + propertyValue);
		}

		runner.withBean(EventSerializer.class, () -> mock(EventSerializer.class))
				.withUserConfiguration(ExampleApplication.class)
				.run(context -> {
					assertThat(AutoConfigurationPackages.get(context)).containsExactlyInAnyOrder(packages);
				});
	}
}
