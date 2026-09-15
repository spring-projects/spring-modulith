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
package org.springframework.modulith.events.core;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.modulith.events.core.TransactionalEventListeners.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.ReflectionUtils;

/**
 * Unit tests for {@link TransactionalEventListeners}.
 *
 * @author Oliver Drotbohm
 * @author Seonwoo Jung
 */
class TransactionalEventListenersUnitTests {

	StandardEnvironment environment = new StandardEnvironment();

	@Test // GH-726
	void onlyConsidersAfterCommitListeners() {

		var afterCommitListener = TransactionalApplicationListener.forPayload(TransactionPhase.AFTER_COMMIT, __ -> {});
		var beforeCommitListener = TransactionalApplicationListener.forPayload(TransactionPhase.BEFORE_COMMIT, __ -> {});

		var eventListeners = new TransactionalEventListeners(List.of(afterCommitListener, beforeCommitListener),
				() -> environment);

		assertThat(eventListeners.stream())
				.hasSize(1)
				.element(0).isEqualTo(afterCommitListener);
	}

	@Test // GH-1630
	void considersTriggerAnnotation() {

		var environment = new MockEnvironment();
		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, ApplicationModuleListener.class.getName());

		var first = getAdapter(ConditionalListener.class, "on", SampleEvent.class);
		var second = getAdapter(ModuleListener.class, "on", SampleEvent.class);

		var listeners = new TransactionalEventListeners(List.of(first, second), () -> environment);

		assertThat(listeners.stream()).containsExactly(second);
	}

	@Test // GH-1630
	void rejectsNotLoadableTriggerAnnotation() {

		var environment = new MockEnvironment();
		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, "some.non.loadable.Type");

		var second = getAdapter(ModuleListener.class, "on", SampleEvent.class);

		assertThatIllegalStateException().isThrownBy(() -> {
			new TransactionalEventListeners(List.of(second), () -> environment);
		});
	}

	@Test // GH-1630
	void rejectsNonAnnotationTypeForTriggerAnnotation() {

		var environment = new MockEnvironment();
		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, "java.lang.String");

		var second = getAdapter(ModuleListener.class, "on", SampleEvent.class);

		assertThatIllegalStateException().isThrownBy(() -> {
			new TransactionalEventListeners(List.of(second), () -> environment);
		});
	}

	private static TransactionalApplicationListenerMethodAdapter getAdapter(Class<?> type, String methodName,
			Class<?> parameter) {

		var method = ReflectionUtils.findMethod(type, methodName, parameter);

		return new TransactionalApplicationListenerMethodAdapter(type.getName(), type, method);
	}

	@Component
	static class ConditionalListener {

		@TransactionalEventListener(condition = "#event.supported")
		void on(SampleEvent event) {}
	}

	@Component
	static class ModuleListener {

		@ApplicationModuleListener
		void on(SampleEvent event) {}
	}

	static class SampleEvent {
		public boolean supported;

		public SampleEvent(boolean supported) {
			this.supported = supported;
		}
	}
}
