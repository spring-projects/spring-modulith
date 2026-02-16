/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.modulith.observability.support;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.Serializable;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.observability.ModulithMetrics;

/**
 * Unit tests for {@link CrossModuleEventCounterFactory}.
 *
 * @author Oliver Drotbohm
 */
class ModuleEventCounterFactoryUnitTests {

	MeterRegistry registry = new SimpleMeterRegistry();
	ModuleEventCounterFactory factory = new ModuleEventCounterFactory();
	ApplicationModuleIdentifier identifier = ApplicationModuleIdentifier.of("someModule");

	@Test // GH-1068
	void appliesCustomization() {

		factory.customize(SampleEvent.class, (__, builder) -> builder.tag("key", "value"));

		var event = new SampleEvent();
		var observedEvent = new ObservedModuleEvent(identifier, event);

		assertCounter(event, it -> {
			assertThat(it.getName()).isEqualTo(observedEvent.getEventCounterName());
			assertThat(it.getTag("key")).isNotNull();
		});
	}

	@Test // GH-1068
	void usesCreatorAndCustomizer() {

		factory.customize(Serializable.class, __ -> Counter.builder("Serializable"));
		factory.customize(SampleEvent.class, __ -> Counter.builder("name"));
		factory.customize(SampleEvent.class, (__, builder) -> builder.tag("key", "value"));
		factory.customize(Object.class, (__, builder) -> builder.tag("key", "value2"));
		factory.customize(OtherSampleEvent.class, (__, builder) -> builder.tag("key2", "value3"));

		assertCounter(new DefaultEvent(), it -> {

			var expected = "%s.%s.%s".formatted(ModulithMetrics.ALL_EVENTS.getName(), identifier,
					DefaultEvent.class.getSimpleName());

			assertThat(it.getName()).isEqualTo(expected);
		});

		assertCounter(new SampleEvent(), it -> {
			assertThat(it.getName()).isEqualTo("name");
			assertThat(it.getTag("key")).isEqualTo("value");
			assertThat(it.getTag("key2")).isNull();
		});

		assertCounter(new OtherSampleEvent(), it -> {
			assertThat(it.getName()).isEqualTo("Serializable");
			assertThat(it.getTag("key")).isEqualTo("value2");
			assertThat(it.getTag("key2")).isEqualTo("value3");
		});
	}

	private void assertCounter(Object event, Consumer<Id> assertions) {

		var observedEvent = new ObservedModuleEvent(identifier, event);

		assertions.accept(factory.createCounterBuilder(observedEvent).register(registry).getId());
	}

	static class DefaultEvent {}

	static class SampleEvent {}

	static class OtherSampleEvent implements Serializable {}
}
