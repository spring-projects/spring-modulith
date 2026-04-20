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

import static org.mockito.Mockito.*;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.StorageProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.core.EventPublicationRegistry;

@Configuration
class JobRunrTestInfrastructure {

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
	JobScheduler jobScheduler(ApplicationContext ctx, StorageProvider storageProvider) {

		return JobRunr.configure()
				.useStorageProvider(storageProvider)
				.useJobActivator(ctx::getBean)
				.useBackgroundJobServer(BackgroundJobServerConfiguration
						.usingStandardBackgroundJobServerConfiguration()
						.andPollIntervalInSeconds(5))
				.initialize()
				.getJobScheduler();
	}

	@Bean
	EventPublicationRegistry registry() {
		return mock(EventPublicationRegistry.class);
	}
}
