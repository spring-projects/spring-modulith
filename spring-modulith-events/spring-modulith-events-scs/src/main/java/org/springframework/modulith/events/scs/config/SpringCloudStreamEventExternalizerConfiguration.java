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
package org.springframework.modulith.events.scs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.scs.SpringCloudStreamEventExternalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

@Configuration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnClass(StreamBridge.class)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true",
        matchIfMissing = true)
public class SpringCloudStreamEventExternalizerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpringCloudStreamEventExternalizerConfiguration.class);

    @Bean
    DelegatingEventExternalizer springCloudStreamMessageExternalizer(EventExternalizationConfiguration configuration,
            StreamBridge streamBridge, BeanFactory factory, BindingServiceProperties bindingServiceProperties,
            BinderFactory binderFactory) {
        log.debug("Registering domain event externalization to Spring Cloud Stream…");

        var context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(factory));

        return new DelegatingEventExternalizer(configuration, new SpringCloudStreamEventExternalizer(configuration,
                context, streamBridge, bindingServiceProperties, binderFactory));
    }

}
