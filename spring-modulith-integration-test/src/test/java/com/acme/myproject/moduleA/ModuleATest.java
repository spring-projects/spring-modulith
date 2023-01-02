/*
 * Copyright 2018-2023 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.PublishedEvents.TypedPublishedEvents;

import com.acme.myproject.NonVerifyingModuleTest;
import com.acme.myproject.moduleB.ServiceComponentB;

/**
 * @author Oliver Drotbohm
 */
@NonVerifyingModuleTest
class ModuleATest {

	@Autowired ApplicationContext context;
	@Autowired PublishedEvents events;

	@Test
	void bootstrapsModuleAOnly() {

		context.getBean(ServiceComponentA.class);

		assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
				.isThrownBy(() -> context.getBean(ServiceComponentB.class));
	}

	@Test
	void assertEventsFired() {

		context.getBean(ServiceComponentA.class).fireEvent();

		TypedPublishedEvents<SomeEventA> matching = events.ofType(SomeEventA.class) //
				.matching(it -> it.getMessage().equals("Message"));

		assertThat(matching).hasSize(1);
	}

	@Test
	void injectsPublishedEventsIntoMethod(PublishedEvents events) {
		assertThat(events).isNotNull();
	}
}
