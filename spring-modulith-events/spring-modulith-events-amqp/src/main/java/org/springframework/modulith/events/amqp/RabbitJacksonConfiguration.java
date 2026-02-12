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

import tools.jackson.databind.json.JsonMapper;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration to configure {@link RabbitTemplate} to use the Jackson {@link JsonMapper} present in the
 * application.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
@ConditionalOnClass({ RabbitTemplate.class, JsonMapper.class })
@ConditionalOnProperty(name = "spring.modulith.events.rabbitmq.enable-json", havingValue = "true",
		matchIfMissing = true)
class RabbitJacksonConfiguration {

	@Bean
	@ConditionalOnBean(JsonMapper.class)
	RabbitTemplateCustomizer rabbitTemplateCustomizer(JsonMapper mapper) {

		return template -> {
			template.setMessageConverter(new JacksonJsonMessageConverter(mapper));
		};
	}
}
