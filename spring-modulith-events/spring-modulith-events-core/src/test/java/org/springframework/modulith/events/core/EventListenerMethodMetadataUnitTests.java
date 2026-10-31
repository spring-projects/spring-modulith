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
import static org.springframework.modulith.events.core.EventListenerMethodMetadata.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.ReflectionUtils;

/**
 * Unit tests for {@link EventListenerMethodMetadata}.
 *
 * @author Oliver Drotbohm
 */
class EventListenerMethodMetadataUnitTests {

	MockEnvironment environment = new MockEnvironment();

	@Test // GH-1630
	void rejectsNotLoadableTriggerAnnotation() {

		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, "some.non.loadable.Type");

		var metadata = EventListenerMethodMetadata.of(() -> environment);

		assertThatIllegalStateException().isThrownBy(() -> metadata.triggersRegistry(method("plain")));
	}

	@Test // GH-1630
	void rejectsNonAnnotationTypeForTriggerAnnotation() {

		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, "java.lang.String");

		var metadata = EventListenerMethodMetadata.of(() -> environment);

		assertThatIllegalStateException().isThrownBy(() -> metadata.triggersRegistry(method("plain")));
	}

	@Test // GH-1903
	void considersAllListenerMethodsByDefault() {

		var metadata = EventListenerMethodMetadata.of(() -> environment);

		assertThat(metadata.triggersRegistry(method("plain"))).isTrue();
		assertThat(metadata.triggersRegistry(method("moduleListener"))).isTrue();
	}

	@Test // GH-1903
	void considersTriggerAnnotation() {

		environment.setProperty(TRIGGER_ANNOTATION_PROPERTY, ApplicationModuleListener.class.getName());

		var metadata = EventListenerMethodMetadata.of(() -> environment);

		assertThat(metadata.triggersRegistry(method("plain"))).isFalse();
		assertThat(metadata.triggersRegistry(method("moduleListener"))).isTrue();
	}

	@Test // GH-1903
	void onlyConsidersAfterCommitListenerMethods() {

		var metadata = EventListenerMethodMetadata.all();

		assertThat(metadata.triggersRegistry(method("afterRollback"))).isFalse();
		assertThat(metadata.triggersRegistry(method("plainEventListener"))).isFalse();
		assertThat(metadata.triggersRegistry(method("nonListener"))).isFalse();
	}

	private static Method method(String name) {

		var method = ReflectionUtils.findMethod(SampleListener.class, name, Object.class);

		assertThat(method).isNotNull();

		return method;
	}

	static class SampleListener {

		@TransactionalEventListener
		void plain(Object event) {}

		@ApplicationModuleListener
		void moduleListener(Object event) {}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
		void afterRollback(Object event) {}

		@EventListener
		void plainEventListener(Object event) {}

		void nonListener(Object event) {}
	}
}
