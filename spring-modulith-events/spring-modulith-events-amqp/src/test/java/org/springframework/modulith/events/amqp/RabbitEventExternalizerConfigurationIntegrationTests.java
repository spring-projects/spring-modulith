/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.events.amqp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.namastack.outbox.handler.OutboxHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.jmolecules.event.annotation.Externalized;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalized;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;
import org.springframework.modulith.events.support.EventExternalizerModuleListener;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.PublishedEventsFactory;

/**
 * Integration tests for {@link RabbitEventExternalizerConfiguration}.
 *
 * @author Oliver Drotbohm
 * @author Roland Beisel
 * @since 1.1
 */
class RabbitEventExternalizerConfigurationIntegrationTests {

	static final EventExternalizationConfiguration EXTERNALIZATION_ENABLED = EventExternalizationConfiguration
			.defaults("org").build();

	@Test
	void requiresCorrelatedPublisherConfirmsForOutboxMode() {

		basicSetupWithoutPublisherConfirms(EXTERNALIZATION_ENABLED, confirmingOperations())
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasFailed();
					assertThat(ctxt.getStartupFailure())
							.hasRootCauseMessage("RabbitMQ outbox event externalization requires "
									+ "spring.rabbitmq.publisher-confirm-type=correlated!");
				});
	}

	@Test
	void doesNotRequireCorrelatedPublisherConfirmsForModuleListenerMode() {

		basicSetupWithoutPublisherConfirms(EXTERNALIZATION_ENABLED, mock(RabbitMessageOperations.class))
				.run(ctxt -> {
					assertThat(ctxt).hasNotFailed();
					assertThat(ctxt).hasSingleBean(EventExternalizerModuleListener.class);
				});
	}

	@Test // GH-342
	void registersExternalizerByDefault() {

		basicSetup()
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(EventExternalizerModuleListener.class);
				});
	}

	@Test // GH-342
	void disablesExternalizationIfConfigured() {

		basicSetup()
				.withPropertyValues("spring.modulith.events.externalization.enabled=false")
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(EventExternalizerModuleListener.class);
				});
	}

	@Test // GH-1637
	void configuresNamastackOutboxHandlerIfPresent() {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(OutboxHandler.class);
				});
	}

	@Test // GH-1637
	void doesNotConfigureNamastackOutboxHandlerIfNotPresent() {

		basicSetup(EXTERNALIZATION_ENABLED, "io.namastack")
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(OutboxHandler.class);
				});
	}

	@Test // GH-1637
	void configuresJobRunrOutboxHandlerIfPresent() {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(JobRunrExternalizationTransport.class);
				});
	}

	@Test // GH-1637
	void doesNotConfigureJobRunrOutboxHandlerIfNotPresent() {

		basicSetup(EXTERNALIZATION_ENABLED, "org.jobrunr")
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(JobRunrExternalizationTransport.class);
				});
	}

	@Test // GH-1642
	void publishesEventExternalizedAfterJobRunrExternalization() {

		assertEventExternalizedPublished(JobRunrExternalizationTransport.class,
				JobRunrExternalizationTransport::externalize);
	}

	@Test // GH-1642
	void publishesEventExternalizedAfterNamastackExternalization() {
		assertEventExternalizedPublished(OutboxHandler.class, (transport, event) -> transport.handle(event, null));
	}

	@Test
	void propagatesSynchronousFailureAsFailedFuture() {

		var operations = mock(RabbitMessageOperations.class);
		var failure = new IllegalStateException("Unable to publish!");

		doThrow(failure).when(operations).convertAndSend(any(), any(), any(), anyMap());

		basicSetup(EXTERNALIZATION_ENABLED, operations)
				.run(ctxt -> {

					var result = ctxt.getBean(EventExternalizerModuleListener.class).externalize(new SampleEvent());

					assertThat(result).isCompletedExceptionally();
					assertThatThrownBy(result::join).hasRootCause(failure);
				});
	}

	@Test
	void completesTransportFutureAfterPositivePublisherConfirm() {

		var correlations = new ArrayList<CorrelationData>();
		var sent = new CountDownLatch(1);
		var operations = capturingOperations(correlations, sent);

		basicSetup(EXTERNALIZATION_ENABLED, operations)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {

					var handler = ctxt.getBean(OutboxHandler.class);
					var result = CompletableFuture.runAsync(() -> handler.handle(new SampleEvent(), null));

					assertThat(sent.await(1, TimeUnit.SECONDS)).isTrue();
					assertThat(result).isNotDone();

					assertThat(correlations).singleElement()
							.satisfies(it -> it.getFuture().complete(new CorrelationData.Confirm(true, null)));

					assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
				});
	}

	@Test
	void completesTransportFutureExceptionallyAfterPublisherNack() {

		assertPublisherNack(JobRunrExternalizationTransport.class, JobRunrExternalizationTransport::externalize);
	}

	@Test
	void moduleListenerDoesNotUsePublisherConfirms() {

		var operations = mock(RabbitMessageOperations.class);

		doAnswer(invocation -> {

			Map<String, Object> headers = invocation.getArgument(3);

			assertThat(headers).doesNotContainKey(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION);

			return null;
		}).when(operations).convertAndSend(any(), any(), any(), anyMap());

		basicSetupWithoutPublisherConfirms(EXTERNALIZATION_ENABLED, operations)
				.run(ctxt -> {

					var result = ctxt.getBean(EventExternalizerModuleListener.class).externalize(new SampleEvent());

					assertThat(result).isCompleted();
				});
	}

	@Test
	void addsUniquePublisherConfirmCorrelationForEachPublish() {

		var correlations = new ArrayList<CorrelationData>();

		basicSetup(EXTERNALIZATION_ENABLED, confirmingOperations(correlations))
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {

					var handler = ctxt.getBean(OutboxHandler.class);

					handler.handle(new SampleEvent(), null);
					handler.handle(new SampleEvent(), null);

					assertThat(correlations)
							.hasSize(2)
							.extracting(CorrelationData::getId)
							.doesNotHaveDuplicates();
				});
	}

	@Test
	void propagatesPublisherNackToNamastackOutboxHandler() {

		assertPublisherNack(OutboxHandler.class, (transport, event) -> transport.handle(event, null));
	}

	private ApplicationContextRunner basicSetup() {
		return basicSetup(null);
	}

	private ApplicationContextRunner basicSetup(@Nullable EventExternalizationConfiguration configuration,
			String... excluded) {

		return basicSetup(configuration, confirmingOperations(), excluded);
	}

	private ApplicationContextRunner basicSetup(@Nullable EventExternalizationConfiguration configuration,
			RabbitMessageOperations operations, String... excluded) {

		return basicSetupWithoutPublisherConfirms(configuration, operations, excluded)
				.withPropertyValues("spring.rabbitmq.publisher-confirm-type=correlated");
	}

	private ApplicationContextRunner basicSetupWithoutPublisherConfirms(
			@Nullable EventExternalizationConfiguration configuration, RabbitMessageOperations operations,
			String... excluded) {

		var defaulted = configuration == null ? EventExternalizationConfiguration.disabled() : configuration;

		var runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						RabbitEventExternalizerConfiguration.class,
						EventExternalizationAutoConfiguration.class))
				.withBean(EventExternalizationConfiguration.class, () -> defaulted)
				.withBean(RabbitMessageOperations.class, () -> operations);

		if (excluded.length > 0) {
			runner = runner.withClassLoader(new FilteredClassLoader(excluded));
		}

		return runner;
	}

	private <T> void assertEventExternalizedPublished(Class<T> transportType, BiConsumer<T, Object> consumer) {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withBean(PublishedEvents.class, PublishedEventsFactory::createPublishedEvents)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {

					var transport = ctxt.getBean(transportType);
					var event = new SampleEvent();

					consumer.accept(transport, event);

					var events = ctxt.getBean(PublishedEvents.class);

					assertThat(events.ofType(EventExternalized.class)
							.matching(it -> it.getEvent().equals(event))).hasSize(1);
				});
	}

	private static RabbitMessageOperations confirmingOperations() {
		return confirmingOperations(new ArrayList<>());
	}

	private static RabbitMessageOperations confirmingOperations(List<CorrelationData> correlations) {

		var operations = mock(RabbitMessageOperations.class);

		doAnswer(invocation -> {

			Map<String, Object> headers = invocation.getArgument(3);
			var correlation = (CorrelationData) headers.get(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION);

			correlations.add(correlation);
			correlation.getFuture().complete(new CorrelationData.Confirm(true, null));

			return null;
		}).when(operations).convertAndSend(any(), any(), any(), anyMap());

		return operations;
	}

	private static RabbitMessageOperations capturingOperations(List<CorrelationData> correlations, CountDownLatch sent) {

		var operations = mock(RabbitMessageOperations.class);

		doAnswer(invocation -> {

			Map<String, Object> headers = invocation.getArgument(3);

			assertThat(headers).containsKey(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION);
			correlations.add((CorrelationData) headers.get(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION));
			sent.countDown();

			return null;
		}).when(operations).convertAndSend(any(), any(), any(), anyMap());

		return operations;
	}

	private <T> void assertPublisherNack(Class<T> transportType, BiConsumer<T, Object> consumer) {

		var operations = mock(RabbitMessageOperations.class);

		doAnswer(invocation -> {

			Map<String, Object> headers = invocation.getArgument(3);
			var correlation = (CorrelationData) headers.get(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION);

			correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));

			return null;
		}).when(operations).convertAndSend(any(), any(), any(), anyMap());

		basicSetup(EXTERNALIZATION_ENABLED, operations)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> assertThatThrownBy(() -> consumer.accept(ctxt.getBean(transportType), new SampleEvent()))
						.hasRootCauseMessage("RabbitMQ publisher confirm was nacked: rejected"));
	}

	@Externalized
	static class SampleEvent {}
}
