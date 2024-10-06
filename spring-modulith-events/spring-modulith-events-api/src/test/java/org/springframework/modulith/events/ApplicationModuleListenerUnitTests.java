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
package org.springframework.modulith.events;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.core.annotation.AnnotatedElementUtils.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit tests for {@link ApplicationModuleListener}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModuleListenerUnitTests {

	@Test // GH-518
	void exposesEventListenerCondition() throws Exception {

		var method = Sample.class.getDeclaredMethod("someMethod", Object.class);
		var annotation = findMergedAnnotation(method, EventListener.class);

		assertThat(annotation.condition()).isEqualTo("#{false}");
	}

	@Test // GH-518
	void exposesConditionToAdapter() throws Exception {

		var adapter = new CustomAdapter("someName", Sample.class.getDeclaredMethod("someMethod", Object.class));

		assertThat(adapter.getCondition()).isEqualTo("#{false}");
	}

	@Test // GH-858
	void declaresCustomTransactionPropagation() throws Exception {

		var method = Sample.class.getDeclaredMethod("withCustomPropagation");
		var annotation = findMergedAnnotation(method, Transactional.class);

		assertThat(annotation.propagation()).isEqualTo(Propagation.SUPPORTS);
	}

	static class Sample {

		@ApplicationModuleListener(condition = "#{false}")
		void someMethod(Object event) {}

		@ApplicationModuleListener(propagation = Propagation.SUPPORTS)
		void withCustomPropagation() {}
	}

	static class CustomAdapter extends ApplicationListenerMethodAdapter {

		public CustomAdapter(String beanName, Method method) {
			super(beanName, method.getDeclaringClass(), method);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.context.event.ApplicationListenerMethodAdapter#getCondition()
		 */
		@Override
		public String getCondition() {
			return super.getCondition();
		}
	}
}
