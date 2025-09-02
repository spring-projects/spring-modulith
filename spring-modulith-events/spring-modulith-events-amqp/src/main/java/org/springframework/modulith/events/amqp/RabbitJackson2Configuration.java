/*
 * Copyright 2023-2025 the original author or authors.
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

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Auto-configuration to configure {@link RabbitTemplate} to use the Jackson {@link ObjectMapper} present in the
 * application.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 * @deprecated since 2.0 in favor of {@link RabbitJacksonConfiguration}
 */
@Deprecated
@AutoConfiguration
@ConditionalOnClass({ RabbitTemplate.class, ObjectMapper.class })
@ConditionalOnProperty(name = "spring.modulith.events.rabbitmq.enable-json", havingValue = "true",
		matchIfMissing = true)
class RabbitJackson2Configuration {

	@Bean
	@ConditionalOnBean(ObjectMapper.class)
	RabbitTemplateCustomizer rabbitTemplateCustomizer(ObjectMapper mapper) {

		return template -> {
			template.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
		};
	}
}
