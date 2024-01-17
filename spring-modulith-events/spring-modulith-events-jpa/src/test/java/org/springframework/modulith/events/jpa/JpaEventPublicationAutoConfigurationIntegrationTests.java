/*
 * Copyright 2022-2024 the original author or authors.
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

import example.ExampleApplication;
import lombok.RequiredArgsConstructor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * @author Oliver Drotbohm
 */
@SpringBootTest
@ContextConfiguration(classes = ExampleApplication.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@RequiredArgsConstructor
class JpaEventPublicationAutoConfigurationIntegrationTests {

	private final BeanFactory factory;

	@MockBean EventSerializer serializer;

	@Test // GH-10
	void registersJpaEventPublicationPackageForAutoConfiguration() {

		var examplePackage = ExampleApplication.class.getPackageName();
		var eventPublicationPackage = JpaEventPublication.class.getPackageName();

		assertThat(AutoConfigurationPackages.get(factory))
				.containsExactlyInAnyOrder(examplePackage, eventPublicationPackage);
	}
}
