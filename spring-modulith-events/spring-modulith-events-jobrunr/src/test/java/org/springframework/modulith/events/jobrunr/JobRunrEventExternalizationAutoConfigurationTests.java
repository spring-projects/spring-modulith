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
package org.springframework.modulith.events.jobrunr;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.mockito.Mockito.*;

import example.SampleApplication;

import java.util.UUID;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.core.EventPublicationRegistry;

/**
 * @author Oliver Drotbohm
 */
class JobRunrEventExternalizationAutoConfigurationTests {

	@Test
	void registersJobRunrEventExternalizer() {

		new ApplicationContextRunner()
				.withPropertyValues(ExternalizationMode.PROPERTY + "=outbox")
				.withUserConfiguration(SampleApplication.class, UserConfig.class)
				.withConfiguration(AutoConfigurations.of(JobRunrEventExternalizationAutoConfiguration.class))
				.run(ctxt -> {

					assertThat(ctxt).hasSingleBean(JobRunrEventExternalizer.class);

					var transportMock = ctxt.getBean(JobRunrExternalizationTransport.class);
					var event = new SampleEvent(UUID.randomUUID());

					ctxt.publishEvent(event);

					await()
							.atMost(5, SECONDS)
							.untilAsserted(() -> verify(transportMock).externalize(event));
				});
	}

	@Externalized
	record SampleEvent(UUID id) {}

	@Configuration
	static class UserConfig {

		@Bean
		EventExternalizationConfiguration eventExternalizationConfiguration() {

			return EventExternalizationConfiguration.externalizing()
					.select(EventExternalizationConfiguration.annotatedAsExternalized())
					.build();
		}

		@Bean
		JobRunrExternalizationTransport transport() {
			return mock(JobRunrExternalizationTransport.class);
		}

		@Bean
		JobScheduler jobScheduler(ApplicationContext ctx) {

			return JobRunr.configure()
					.useStorageProvider(new InMemoryStorageProvider())
					.useJobActivator(ctx::getBean)
					.useBackgroundJobServer(BackgroundJobServerConfiguration
							.usingStandardBackgroundJobServerConfiguration()
							.andPollIntervalInSeconds(1))
					.initialize()
					.getJobScheduler();
		}

		@Bean
		EventPublicationRegistry registry() {
			return mock(EventPublicationRegistry.class);
		}
	}
}
