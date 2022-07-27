/*
 * Copyright 2018-2022 the original author or authors.
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
package com.acme.myproject.complex;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.model.NamedInterface;
import org.springframework.modulith.model.NamedInterfaces;
import org.springframework.modulith.test.ModuleTestExecution;

import com.acme.myproject.NonVerifyingModuleTest;

/**
 * @author Oliver Drotbohm
 */
@NonVerifyingModuleTest
class ComplexTest {

	@Autowired ModuleTestExecution moduleTest;

	@Test
	void exposesNamedInterfaces() {

		NamedInterfaces interfaces = moduleTest.getModule().getNamedInterfaces();

		assertThat(interfaces.stream().map(NamedInterface::getName)) //
				.containsExactlyInAnyOrder("API", "SPI", "Port 1", "Port 2", "Port 3");
	}
}
