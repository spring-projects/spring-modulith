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
package org.springframework.modulith.events.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.support.BrokerRouting.SpelBrokerRouting;

/**
 * Unit tests for {@link BrokerRouting}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class BrokerRoutingUnitTests {

	@Test // GH-248
	void parsesSpelExpressionFromTarget() {

		var target = RoutingTarget.forTarget("target").andKey("#{@bean.getKey(#this) + someKey()}");

		var routing = BrokerRouting.of(target, getEvaluationContext());

		assertThat(routing).isInstanceOf(SpelBrokerRouting.class);
		assertThat(routing.getKey(new TestEvent())).isEqualTo("foofoo");
	}

	@Test // GH-248
	void doesNotAccessEvaluationContextIfNoSpelExpression() {

		var context = mock(EvaluationContext.class);

		var routing = BrokerRouting.of(RoutingTarget.forTarget("target").andKey("key"), context);

		assertThat(routing).isNotInstanceOf(SpelBrokerRouting.class);
		assertThat(routing.getKey(new TestEvent())).isEqualTo("key");
		verifyNoInteractions(context);
	}

	private static EvaluationContext getEvaluationContext() {

		var evaluationContext = new StandardEvaluationContext();
		evaluationContext.setBeanResolver((context, name) -> new TestBean());

		return evaluationContext;
	}

	static class TestEvent {

		public String someKey() {
			return "foo";
		}
	}

	static class TestBean {

		public String getKey(TestEvent event) {
			return event.someKey();
		}
	}
}
