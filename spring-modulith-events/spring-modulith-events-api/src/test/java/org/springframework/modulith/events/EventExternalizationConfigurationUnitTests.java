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
package org.springframework.modulith.events;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.modulith.events.EventExternalizationConfiguration.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultEventExternalizationConfiguration}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class EventExternalizationConfigurationUnitTests {

	@Test // GH-248
	void filtersEventByAnnotation() {

		var filter = externalizing()
				.selectByAnnotation(CustomExternalized.class)
				.build();

		var event = new SampleEvent();

		assertThat(filter.supports(event)).isTrue();
		assertThat(filter.supports(new Object())).isFalse();
		assertThat(filter.determineTarget(event))
				.isEqualTo(RoutingTarget.forTarget(SampleEvent.class.getName()).withoutKey());
	}

	@Test // GH-248
	void routesByAnnotationAttribute() {

		var filter = externalizing()
				.selectAndRoute(CustomExternalized.class, CustomExternalized::value)
				.build();

		var event = new SampleEvent();

		assertThat(filter.supports(event)).isTrue();
		assertThat(filter.determineTarget(event))
				.isEqualTo(RoutingTarget.forTarget("target").withoutKey());
	}

	@Test // GH-248
	void mapsSourceEventBeforeSerializing() {

		var configuration = externalizing()
				.select(__ -> true)
				.mapping(SampleEvent.class, it -> "foo")
				.mapping(AnotherSampleEvent.class, it -> "bar")
				.build();

		assertThat(configuration.map(new SampleEvent())).isEqualTo("foo");
		assertThat(configuration.map(new AnotherSampleEvent())).isEqualTo("bar");
		assertThat(configuration.map(4711L)).isEqualTo(4711L);
	}

	@Test // GH-248
	void setsUpMappedRouting() {

		var configuration = externalizing()
				.select(__ -> true)
				.mapping(SampleEvent.class, it -> "foo")
				.routeMapped()
				.build();

		assertThat(configuration.determineTarget(new SampleEvent()))
				.isEqualTo(RoutingTarget.forTarget(String.class.getName()).withoutKey());
	}

	@Test // GH-248
	void registersMultipleTypeBasedRouters() {

		var configuration = externalizing()
				.select(annotatedAsExternalized())
				.routeKey(WithKeyProperty.class, WithKeyProperty::getKey)
				.build();

		assertThat(configuration.determineTarget(new WithKeyProperty("key")).getKey()).isEqualTo("key");

		var undefined = configuration.determineTarget(new Object());

		assertThat(undefined.getTarget()).isEqualTo(Object.class.getName());
		assertThat(undefined.getKey()).isNull();
	}

	@Test // GH-248
	void addsFallbackTargetIfNotSpecifiedInAnnotation() {

		var configuration = externalizing()
				.select(__ -> true)
				.build();

		assertThat(configuration.determineTarget(new KeyOnlyAnnotated()))
				.isEqualTo(RoutingTarget.forTarget(KeyOnlyAnnotated.class.getName()).andKey("key"));
	}

	@Test // GH-248
	void defaultSetup() {

		var configuration = defaults("org.springframework.modulith").build();

		var target = configuration.determineTarget(new AnotherSampleEvent());

		var expected = AnotherSampleEvent.class.getName();
		expected = expected.substring(expected.indexOf("events"));

		assertThat(target.getTarget()).isEqualTo(expected);
		assertThat(target.getKey()).isNull();
	}

	@Test // GH-855
	void registersHeaderExtractor() {

		var configuration = defaults("org.springframework.modulith")
				.headers(AnotherSampleEvent.class, it -> Map.of("another", "anotherValue"))
				.headers(SampleEvent.class, it -> Map.of("sample", "value"))
				.build();

		assertThat(configuration.getHeadersFor(new SampleEvent()))
				.containsEntry("sample", "value");

		assertThat(configuration.getHeadersFor(new AnotherSampleEvent()))
				.containsEntry("another", "anotherValue");
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface CustomExternalized {
		String value() default "";
	}

	@CustomExternalized("target")
	static class SampleEvent {}

	static class AnotherSampleEvent {}

	@Externalized("::key")
	static class KeyOnlyAnnotated {}

	static class WithKeyProperty {

		private final String key;

		WithKeyProperty(String key) {
			this.key = key;
		}

		String getKey() {
			return key;
		}
	}
}
