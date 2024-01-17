/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.modulith.events.aot;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Integration tests for {@link TransactionalEventListenerAotProcessor}.
 *
 * @author Oliver Drotbohm
 */
class TransactionalEventListenerAotProcessorIntegrationTests {

	@Test // GH-349
	void aotCustomizationsDiscoverable() {

		assertThat(AotServices.factories().load(BeanRegistrationAotProcessor.class))
				.anyMatch(TransactionalEventListenerAotProcessor.class::isInstance);
	}

	@Test // GH-349
	void registersEventListenerMethodParametersForReflection() {

		var factory = new DefaultListableBeanFactory();
		factory.registerBeanDefinition("sample", new RootBeanDefinition(Sample.class));

		var contribution = new TransactionalEventListenerAotProcessor()
				.processAheadOfTime(RegisteredBean.of(factory, "sample"));

		assertThat(contribution).isNotNull();

		var context = new TestGenerationContext();

		contribution.applyTo(context, null);

		assertThat(RuntimeHintsPredicates.reflection()
				.onType(MyEvent.class)
				.withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
						MemberCategory.INVOKE_DECLARED_METHODS)).accepts(context.getRuntimeHints());
	}

	static class Sample {

		@TransactionalEventListener
		void on(MyEvent event) {}
	}

	record MyEvent(String payload) {}
}
