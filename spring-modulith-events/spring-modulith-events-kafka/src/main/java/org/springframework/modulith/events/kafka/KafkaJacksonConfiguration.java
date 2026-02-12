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
package org.springframework.modulith.events.kafka;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

/**
 * Auto-configures Spring for Apache Kafka to use JSON as transport format by default via Jackson 3.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
@AutoConfiguration
@AutoConfigureBefore({ KafkaAutoConfiguration.class, KafkaJackson2Configuration.class })
@ConditionalOnClass(JsonMapper.class)
@ConditionalOnProperty(name = "spring.modulith.events.kafka.enable-json", havingValue = "true", matchIfMissing = true)
@PropertySource("classpath:kafka-json.properties")
class KafkaJacksonConfiguration {

	@Bean
	@ConditionalOnMissingBean(RecordMessageConverter.class)
	@ConditionalOnClass(JsonMapper.class)
	ByteArrayJacksonJsonMessageConverter jacksonJsonMessageConverter(ObjectProvider<JsonMapper> mapper) {
		return new ByteArrayJacksonJsonMessageConverter(mapper.getIfUnique(() -> new JsonMapper()));
	}
}
