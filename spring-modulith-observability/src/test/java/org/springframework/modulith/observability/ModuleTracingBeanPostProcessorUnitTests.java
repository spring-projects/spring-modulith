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
package org.springframework.modulith.observability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import example.sample.SampleProperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * Unit tests for {@link ModuleTracingBeanPostProcessor}.
 *
 * @author Oliver Drotbohm
 */
class ModuleTracingBeanPostProcessorUnitTests {

	@Test // GH-498
	void doesNotProxyConfiguationProperties() {

		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("properties", new RootBeanDefinition(SampleProperties.class));

		var mock = mock(ApplicationModulesRuntime.class);
		doReturn(SampleProperties.class).when(mock).getUserClass(any(), any());
		doReturn(true).when(mock).isApplicationClass(any());

		var processor = new ModuleTracingBeanPostProcessor(mock, () -> null, beanFactory);

		var bean = new SampleProperties();
		var result = processor.postProcessAfterInitialization(bean, "properties");

		assertThat(result).isSameAs(bean);
	}
}
