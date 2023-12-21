/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.modulith.events.config;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties.Shutdown;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration.AsyncPropertiesDefaulter;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.ProxyAsyncConfiguration;
import org.springframework.scheduling.aspectj.AspectJAsyncConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link EventPublicationConfiguration}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class EventPublicationAutoConfigurationIntegrationTests {

	@Mock EventPublicationRepository repository;

	@Test // GH-149, GH-371
	void doesNotRegisterAsyncTerminationDefaulterByDefault() {

		basicSetup().run(context -> {

			assertThat(context).hasSingleBean(AsyncPropertiesDefaulter.class);

			expect(Shutdown::isAwaitTermination, false);
			expect(Shutdown::getAwaitTerminationPeriod, null);
		});
	}

	@Test // GH-149
	void doesNotRegisterDefaulterIfDisabledExplicitly() {

		basicSetup()
				.withPropertyValues("spring.modulith.default-async-termination=false")
				.run(context -> assertThat(context).doesNotHaveBean(AsyncPropertiesDefaulter.class));
	}

	@Test // GH-149
	void doesNotApplyDefaultingIfShutdownTerminationPropertyConfigured() {

		basicSetup()
				.withPropertyValues("spring.task.execution.shutdown.await-termination=false")
				.run(expect(Shutdown::isAwaitTermination, false));
	}

	@Test // GH-149
	void doesNotApplyDefaultingIfShutdownTerminationPeriodPropertyConfigured() {

		basicSetup()
				.withPropertyValues("spring.task.execution.shutdown.await-termination-period=10m")
				.run(expect(Shutdown::getAwaitTerminationPeriod, Duration.ofMinutes(10)));
	}

	@Test // GH-184
	void enablesAsyncSupportByDefault() {

		basicSetup().run(context -> {
			assertThat(context).hasSingleBean(ProxyAsyncConfiguration.class);
		});
	}

	@Test // GH-184
	void doesNotEnableAsyncSupportByDefaultIfExplicitlyConfigured() {

		basicSetup()
				.withUserConfiguration(CustomAsyncConfiguration.class)
				.run(context -> {
					assertThat(context)
							.doesNotHaveBean(ProxyAsyncConfiguration.class)
							.hasSingleBean(AspectJAsyncConfiguration.class);
				});
	}

	@Test // GH-206
	void wiresCustomClockIntoEventPublicationRegistryIfConfigured() {

		var clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

		basicSetup()
				.withBean(Clock.class, () -> clock)
				.run(context -> {

					var registry = context.getBean(EventPublicationRegistry.class);

					assertThat(ReflectionTestUtils.getField(registry, "clock")).isSameAs(clock);
				});
	}

	@Test // GH-294
	void exposesCompletedAndIncompleteEventPublications() {

		basicSetup().run(context -> {
			assertThat(context)
					.hasSingleBean(CompletedEventPublications.class)
					.hasSingleBean(IncompleteEventPublications.class);
		});
	}

	@Test // GH-374
	void compatibleWithEnablePersistentDomainEvents() {
		basicSetup()
				.withUserConfiguration(EnablePersistentDomainEventConfiguration.class)
				.run(context -> {
					assertThat(context).hasNotFailed();
				});
	}

	private static <T> ContextConsumer<AssertableApplicationContext> expect(Function<Shutdown, T> extractor,
			@Nullable T expected) {

		return context -> assertThat(context.getBean(TaskExecutionProperties.class).getShutdown())
				.extracting(extractor)
				.isEqualTo(expected);
	}

	private ApplicationContextRunner basicSetup() {

		return new ApplicationContextRunner()
				.withConfiguration(
						AutoConfigurations.of(EventPublicationAutoConfiguration.class, TaskExecutionAutoConfiguration.class))
				.withBean(EventPublicationRepository.class, () -> repository);
	}

	@EnableAsync(mode = AdviceMode.ASPECTJ)
	static class CustomAsyncConfiguration {}

	@EnablePersistentDomainEvents
	static class EnablePersistentDomainEventConfiguration {}
}
