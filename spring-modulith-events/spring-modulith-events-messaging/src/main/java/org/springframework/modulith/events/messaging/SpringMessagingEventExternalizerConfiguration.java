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
package org.springframework.modulith.events.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

import java.util.concurrent.CompletableFuture;

/**
 * Auto-configuration to set up a {@link DelegatingEventExternalizer} to externalize events to a Spring Messaging
 * {@link MessageChannel message channel}.
 *
 * @author Josh Long
 */
@AutoConfiguration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnClass(MessageChannel.class)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled",
        havingValue = "true",
        matchIfMissing = true)
class SpringMessagingEventExternalizerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SpringMessagingEventExternalizerConfiguration.class);

    public static final String MODULITH_ROUTING_HEADER = "modulithRouting";

    @AutoConfiguration
    @ConditionalOnMissingBean(value = MessageChannel.class, annotation = ModulithEventsMessageChannel.class)
    @ConditionalOnClass(MessageChannel.class)
    static class IntegrationConfiguration {


        @Bean
        @ModulithEventsMessageChannel
        DirectChannelSpec modulithEventsMessageChannel() {
            return MessageChannels.direct();
        }
    }

    @Bean
    DelegatingEventExternalizer springMessagingEventExternalizer(
            EventExternalizationConfiguration configuration,
            @ModulithEventsMessageChannel MessageChannel modulithEventsMessageChannel,
            BeanFactory factory) {

        logger.debug("Registering domain event externalization for Spring Messaging…");

        var context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(factory));

        return new DelegatingEventExternalizer(configuration, (target, payload) -> {
            var routing = BrokerRouting.of(target, context);
            var message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MODULITH_ROUTING_HEADER, routing)
                    .build();
            modulithEventsMessageChannel.send(message);
            return CompletableFuture.completedFuture(null);
        });
    }
}
