/*
 * Copyright 2017-2026 the original author or authors.
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
package org.springframework.modulith.events.jackson;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.config.EventSerializationConfigurationExtension;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.util.Assert;

/**
 * Application configuration to register a Jackson 3-based {@link EventSerializer}.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
@AutoConfigureBefore(name = "org.springframework.modulith.events.jackson2.Jackson2EventSerializationConfiguration")
@ConditionalOnClass(JsonMapper.class)
@ConditionalOnMissingBean(EventSerializer.class)
class JacksonEventSerializationConfiguration implements EventSerializationConfigurationExtension {

	private final ObjectProvider<JsonMapper> mapper;
	private final ApplicationContext context;

	/**
	 * Creates a new {@link JacksonEventSerializationConfiguration} for the given {@link JsonMapper} and
	 * {@link ApplicationContext}.
	 *
	 * @param mapper must not be {@literal null}.
	 * @param context must not be {@literal null}.
	 */
	public JacksonEventSerializationConfiguration(ObjectProvider<JsonMapper> mapper, ApplicationContext context) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");
		Assert.notNull(context, "ApplicationContext must not be null!");

		this.mapper = mapper;
		this.context = context;
	}

	@Bean
	JacksonEventSerializer jacksonEventSerializer() {
		return new JacksonEventSerializer(() -> mapper.getIfAvailable(() -> defaultMapper()));
	}

	private JsonMapper defaultMapper() {

		return JsonMapper.builder()
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
				.addModules(context.getBeansOfType(JacksonModule.class).values())
				.build();
	}
}
