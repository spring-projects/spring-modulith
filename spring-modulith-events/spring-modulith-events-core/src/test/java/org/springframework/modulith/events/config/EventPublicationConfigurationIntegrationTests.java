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

import java.time.Duration;
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
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.config.EventPublicationConfiguration.AsyncPropertiesDefaulter;

/**
 * Unit tests for {@link EventPublicationConfiguration}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class EventPublicationConfigurationIntegrationTests {

	@Mock EventPublicationRepository repository;

	@Test // GH-149
	void registersAsyncTerminationDefaulterByDefault() {

		basicSetup()
				.run(context -> assertThat(context).hasSingleBean(AsyncPropertiesDefaulter.class));
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
				.withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
				.withPropertyValues("spring.task.execution.shutdown.await-termination=false")
				.run(expect(Shutdown::isAwaitTermination, false));
	}

	@Test // GH-149
	void doesNotApplyDefaultingIfShutdownTerminationPeriodPropertyConfigured() {

		basicSetup()
				.withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
				.withPropertyValues("spring.task.execution.shutdown.await-termination-period=10m")
				.run(expect(Shutdown::getAwaitTerminationPeriod, Duration.ofMinutes(10)));
	}

	private <T> ContextConsumer<AssertableApplicationContext> expect(Function<Shutdown, T> extractor,
			T expected) {

		return context -> assertThat(context.getBean(TaskExecutionProperties.class).getShutdown())
				.extracting(extractor)
				.isEqualTo(expected);
	}

	private ApplicationContextRunner basicSetup() {

		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(EventPublicationConfiguration.class))
				.withBean(EventPublicationRepository.class, () -> repository);
	}
}
