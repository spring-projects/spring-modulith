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
package org.springframework.modulith.observability.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import example.ExampleApplication;
import example.sample.ContextSampleComponent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RootResourceInformation;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.observability.ModulithContext;
import org.springframework.modulith.observability.ModulithObservationConvention;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.modulith.test.TestApplicationModules;

/**
 * Integration tests for context-local module observations.
 *
 * @author Dmytro Shubchynskyi
 */
class ModuleObservabilityContextIntegrationTests {

	static final ApplicationModules MODULES = TestApplicationModules.of(ExampleApplication.class);

	@Test // GH-1968
	void isolatesRegistriesAndErrorsAcrossContexts() {

		var first = new RecordingHandler();
		var second = new RecordingHandler();

		try (var firstContext = context("first", registry(first));
				var secondContext = context("second", registry(second))) {

			assertThatIllegalStateException().isThrownBy(firstContext.getBean(ContextSampleComponent.class)::fail);
			assertThatIllegalStateException().isThrownBy(secondContext.getBean(ContextSampleComponent.class)::fail);

			assertThat(first.observations).singleElement().satisfies(it -> {
				assertThat(it.getApplicationName()).isEqualTo("first");
				assertThat(it.getName()).isEqualTo("first.invocations");
				assertThat(it.getError()).isInstanceOf(IllegalStateException.class);
			});
			assertThat(second.observations).singleElement().satisfies(it -> {
				assertThat(it.getApplicationName()).isEqualTo("second");
				assertThat(it.getName()).isEqualTo("second.invocations");
				assertThat(it.getError()).isInstanceOf(IllegalStateException.class);
			});
		}
	}

	@Test // GH-1968
	void isolatesEnvironmentAndConventionWhenRegistryIsShared() {

		var handler = new RecordingHandler();
		var registry = registry(handler);

		try (var first = context("first", registry); var second = context("second", registry)) {

			first.getBean(ContextSampleComponent.class).invoke(() -> {});
			second.getBean(ContextSampleComponent.class).invoke(() -> {});

			assertThat(handler.observations).extracting(ModulithContext::getApplicationName)
					.containsExactly("first", "second");
			assertThat(handler.observations).extracting(ModulithContext::getName)
					.containsExactly("first.invocations", "second.invocations");
		}
	}

	@Test // GH-1968
	void isolatesDataRestObservationsAcrossContexts() {

		var first = new RecordingHandler();
		var second = new RecordingHandler();
		var resource = resourceInformation();

		try (var firstContext = context("first", registry(first));
				var secondContext = context("second", registry(second))) {

			assertThatIllegalStateException().isThrownBy(() -> firstContext.getBean(SampleController.class)
					.invoke(resource, () -> { throw new IllegalStateException("First failure"); }));
			assertThatIllegalStateException().isThrownBy(() -> secondContext.getBean(SampleController.class)
					.invoke(resource, () -> { throw new IllegalStateException("Second failure"); }));

			assertThat(first.observations).singleElement().satisfies(it -> {
				assertThat(it.getApplicationName()).isEqualTo("first");
				assertThat(it.getName()).isEqualTo("first.invocations");
				assertThat(it.getError()).hasMessage("First failure");
			});
			assertThat(second.observations).singleElement().satisfies(it -> {
				assertThat(it.getApplicationName()).isEqualTo("second");
				assertThat(it.getName()).isEqualTo("second.invocations");
				assertThat(it.getError()).hasMessage("Second failure");
			});
		}
	}

	@Test // GH-1968
	void reusesDataRestInterceptorWithinController() {

		var handler = new RecordingHandler();
		var registry = registry(handler);
		var lookups = new AtomicInteger();
		Supplier<ObservationRegistry> supplier = () -> {
			lookups.incrementAndGet();
			return registry;
		};

		try (var context = context("rest", registry, supplier)) {

			lookups.set(0);

			var controller = context.getBean(SampleController.class);
			controller.invoke(resourceInformation(), () -> {});
			controller.invoke(resourceInformation(), () -> {});

			assertThat(handler.observations).hasSize(2);
			assertThat(lookups.get()).isOne();
		}
	}

	@Test // GH-1968
	void preservesParentAndSuppressesNestedEntriesIntoSameModule() {

		var handler = new RecordingHandler();
		var registry = registry(handler);

		try (var context = context("nested", registry)) {

			var bean = context.getBean(ContextSampleComponent.class);
			var parent = Observation.start("parent", registry);

			try (var scope = parent.openScope()) {
				bean.invoke(() -> bean.invoke(() -> {}));
				context.getBean(SampleController.class).invoke(resourceInformation(), () -> bean.invoke(() -> {}));
			} finally {
				parent.stop();
			}

			assertThat(handler.observations).hasSize(2).allSatisfy(it -> {
				assertThat(it.getParentObservation()).isSameAs(parent);
				assertThat(it.getApplicationName()).isEqualTo("nested");
			});
		}
	}

	private static GenericApplicationContext context(String name, ObservationRegistry registry) {
		return context(name, registry, () -> registry);
	}

	private static GenericApplicationContext context(String name, ObservationRegistry registry,
			Supplier<ObservationRegistry> supplier) {

		var context = new GenericApplicationContext();
		context.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("application", Map.of("spring.application.name", name)));
		context.getBeanFactory().registerSingleton("observationRegistry", registry);
		context.registerBean(ExampleApplication.class);
		context.registerBean(ContextSampleComponent.class);
		context.registerBean(SampleController.class);

		var runtime = new ApplicationModulesRuntime(() -> MODULES, ApplicationRuntime.of(context));
		Supplier<ModulithObservationConvention> convention = () -> new ModulithObservationConvention() {
			@Override
			public String getName() {
				return name + ".invocations";
			}
		};
		var processor = new ModuleObservabilityBeanPostProcessor(runtime, supplier, convention,
				context.getBeanFactory(), context.getEnvironment());
		processor.setBeanFactory(context.getBeanFactory());
		context.getBeanFactory().addBeanPostProcessor(processor);
		context.getBeanFactory().addBeanPostProcessor(new SpringDataRestModuleObservabilityBeanPostProcessor(
				runtime, supplier, convention, context.getEnvironment()));
		context.refresh();

		return context;
	}

	private static ObservationRegistry registry(RecordingHandler handler) {

		var registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(handler);
		return registry;
	}

	private static RootResourceInformation resourceInformation() {

		var resource = mock(RootResourceInformation.class);
		doReturn(ContextSampleComponent.class).when(resource).getDomainType();
		return resource;
	}

	@BasePathAwareController
	static class SampleController {

		public void invoke(RootResourceInformation resource, Runnable action) {
			action.run();
		}
	}

	static class RecordingHandler implements ObservationHandler<Observation.Context> {

		final List<ModulithContext> observations = new ArrayList<>();

		@Override
		public boolean supportsContext(Observation.Context context) {
			return true;
		}

		@Override
		public void onStop(Observation.Context context) {
			if (context instanceof ModulithContext module) {
				observations.add(module);
			}
		}
	}
}
