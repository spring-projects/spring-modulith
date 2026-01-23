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
package com.acme.myproject.moduleA;

import static org.assertj.core.api.Assertions.*;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.modulith.test.ModuleSlicing;

import com.acme.myproject.moduleB.ServiceComponentB;

/**
 * @author Oliver Drotbohm
 */
@ModuleSlicing(verifyAutomatically = false)
@JsonTest
class CombinedTests {

	@Autowired JsonMapper mapper;
	@Autowired ObjectProvider<ServiceComponentB> serviceComponentB;

	@Test
	void bootstrapsSlicedJsonTest() {

		assertThat(mapper).isNotNull();
		assertThat(serviceComponentB.stream()).isEmpty();
	}
}
