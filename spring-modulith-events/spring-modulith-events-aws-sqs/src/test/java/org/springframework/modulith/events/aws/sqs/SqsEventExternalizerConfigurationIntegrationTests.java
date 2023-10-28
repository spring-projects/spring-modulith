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
package org.springframework.modulith.events.aws.sqs;

import io.awspring.cloud.sqs.operations.SqsOperations;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link SqsEventExternalizerConfiguration}.
 *
 * @author Maciej Walkowiak
 * @since 1.1
 */
class SqsEventExternalizerConfigurationIntegrationTests {

	@Test // GH-342
	void registersExternalizerByDefault() {

		basicSetup()
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(DelegatingEventExternalizer.class);
				});
	}

	@Test // GH-342
	void disablesExternalizationIfConfigured() {

		basicSetup()
				.withPropertyValues("spring.modulith.events.externalization.enabled=false")
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(DelegatingEventExternalizer.class);
				});
	}

	private ApplicationContextRunner basicSetup() {

		return new ApplicationContextRunner()
				.withConfiguration(
						AutoConfigurations.of(SqsEventExternalizerConfiguration.class))
				.withBean(EventExternalizationConfiguration.class, () -> EventExternalizationConfiguration.disabled())
				.withBean(SqsOperations.class, () -> mock(SqsOperations.class));
	}
}
