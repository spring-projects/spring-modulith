/*
 * Copyright 2022-2023 the original author or authors.
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
package org.springframework.modulith.runtime.autoconfigure;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.runtime.ApplicationRuntime;

/**
 * Unit tests for {@link SpringBootApplicationRuntime}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class SpringBootApplicationRuntimeUnitTests {

	@Mock ApplicationContext context;

	@Test // GH-87
	void extractsUserTypeFromClassBasedProxy() {

		Object proxy = new ProxyFactory(new Sample()).getProxy();
		ApplicationRuntime runtime = new SpringBootApplicationRuntime(context);

		assertThat(proxy.getClass()).isNotEqualTo(Sample.class);
		assertThat(runtime.getUserClass(proxy, "sample")).isEqualTo(Sample.class);
	}

	static class Sample {}
}
