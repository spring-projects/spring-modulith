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
package org.springframework.modulith.events.aws.sns;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.Externalized;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for SQS-based event publication.
 *
 * @author Maciej Walkowiak
 */
@SpringBootTest
class SnsEventPublicationIntegrationTests {

    @Autowired TestPublisher publisher;
    @Autowired SnsClient snsClient;
    @Autowired SqsAsyncClient sqsAsyncClient;

    @SpringBootApplication
    static class TestConfiguration {

        @Bean
        LocalStackContainer localStackContainer(DynamicPropertyRegistry registry) {
            var localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.3.2"));
            registry.add("spring.cloud.aws.endpoint", localstack::getEndpoint);
            registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
            registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
            registry.add("spring.cloud.aws.region.static", localstack::getRegion);
            return localstack;
        }

        @Bean
        TestPublisher testPublisher(ApplicationEventPublisher publisher) {
            return new TestPublisher(publisher);
        }

        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }

    @Test
    void publishesEventToSns() {

        var topicArn = snsClient.createTopic(request -> request.name("target")).topicArn();

        var queueUrl = sqsAsyncClient.createQueue(request -> request.queueName("queue"))
                .join()
                .queueUrl();

        var queueArn = sqsAsyncClient
                .getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .join().attributes().get(QueueAttributeName.QUEUE_ARN);
        snsClient.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(queueArn));

        publisher.publishEvent();

        await().untilAsserted(() -> {
            var response = sqsAsyncClient.receiveMessage(r -> r.queueUrl(queueUrl)).join();

            assertThat(response.hasMessages()).isTrue();
        });
    }

    @Test
    void publishesEventWithGroupIdToSns() {

        var topicArn = snsClient.createTopic(request -> request.name("target.fifo")
                        .attributes(Map.of(
                                "FifoTopic", "true",
                                "ContentBasedDeduplication", "true"
                        )))
                .topicArn();

        var queueUrl = sqsAsyncClient.createQueue(request -> request.queueName("queue.fifo")
                        .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true")))
                .join()
                .queueUrl();

        var queueArn = sqsAsyncClient
                .getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .join().attributes().get(QueueAttributeName.QUEUE_ARN);
        snsClient.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(queueArn));

        publisher.publishEventWithKey();

        await().untilAsserted(() -> {
            var response = sqsAsyncClient.receiveMessage(r -> r.queueUrl(queueUrl)).join();
            assertThat(response.hasMessages()).isTrue();
        });
    }

    @Externalized("target")
    static class TestEvent { }

    @Externalized("target.fifo::#{getKey()}")
    static class TestEventWithKey {
        private final String key;

        TestEventWithKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    @RequiredArgsConstructor
    static class TestPublisher {

        private final ApplicationEventPublisher events;

        @Transactional
        void publishEvent() {
            events.publishEvent(new TestEvent());
        }

        @Transactional
        void publishEventWithKey() {
            events.publishEvent(new TestEventWithKey("aKey"));
        }
    }

    static class TestListener {

        @ApplicationModuleListener
        void on(TestEvent event) {
        }

        @ApplicationModuleListener
        void on(TestEventWithKey event) {
        }
    }
}
