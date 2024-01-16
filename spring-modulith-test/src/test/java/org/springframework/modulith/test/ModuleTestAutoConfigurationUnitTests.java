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
package org.springframework.modulith.test;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;

/**
 * Unit tests for {@link ModuleTestAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class ModuleTestAutoConfigurationUnitTests {

	@Test // GH-449
	void handlesNoConstructorArgumentOnPackagesBeanDefinition() {

		var definition = new RootBeanDefinition(AutoConfigurationPackages.class);
		var factory = new DefaultListableBeanFactory();
		factory.registerBeanDefinition(ModuleTestAutoConfiguration.AUTOCONFIG_PACKAGES, definition);

		assertThatNoException().isThrownBy(() -> {
			new ModuleTestAutoConfiguration.AutoConfigurationAndEntityScanPackageCustomizer()
					.setBasePackagesOn(factory, ModuleTestAutoConfiguration.AUTOCONFIG_PACKAGES, Collections.emptyList());
		});
	}
}
