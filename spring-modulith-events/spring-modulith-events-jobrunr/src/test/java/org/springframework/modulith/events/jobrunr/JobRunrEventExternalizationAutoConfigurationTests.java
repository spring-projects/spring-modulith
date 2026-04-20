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

import javax.sql.DataSource;

import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.Externalized;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@link JobRunrEventExternalizationAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
class JobRunrEventExternalizationAutoConfigurationTests {

	@Test
	void registersJobRunrEventExternalizer() {

		createRunner()
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

	@Test // GH-1655
	void setupPreventsJobSchedulerFromPrematurelyCommitting() {

		createRunner()
				.run(ctxt -> {

					var dataSource = ctxt.getBean(DataSource.class);
					var externalizer = ctxt.getBean(JobRunrEventExternalizer.class);
					var scheduler = ctxt.getBean(StorageProvider.class);

					var before = scheduler.countJobs(StateName.ENQUEUED);

					var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

					tx.executeWithoutResult(status -> {

						var event = new PayloadApplicationEvent<>(this, new SampleEvent(UUID.randomUUID()));

						// Publish event, which enqueues a job
						externalizer.onApplicationEvent(event);

						// Roll back!
						status.setRollbackOnly();
					});

					// Make sure the enqueued job was not committed
					assertThat(scheduler.countJobs(StateName.ENQUEUED)).isEqualTo(before);

					tx.executeWithoutResult(status -> {

						var event = new PayloadApplicationEvent<>(this, new SampleEvent(UUID.randomUUID()));

						// Publish event, which enqueues a job
						externalizer.onApplicationEvent(event);

						// Do not roll back
					});

					// Make sure that job was enqueued
					assertThat(scheduler.countJobs(StateName.ENQUEUED)).isEqualTo(before + 1);
				});
	}

	private static ApplicationContextRunner createRunner() {

		return new ApplicationContextRunner()
				.withPropertyValues(
						ExternalizationMode.PROPERTY + "=outbox",
						"logging.level.org.h2=debug")
				.withUserConfiguration(SampleApplication.class, JobRunrTestInfrastructure.class)
				.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
						ModulithJobRunrSqlStorageProviderAutoConfiguration.class,
						JobRunrEventExternalizationAutoConfiguration.class));
	}

	@Externalized
	record SampleEvent(UUID id) {}
}
