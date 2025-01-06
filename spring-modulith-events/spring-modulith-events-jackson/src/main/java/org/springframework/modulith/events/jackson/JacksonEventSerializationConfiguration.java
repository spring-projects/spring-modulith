/*
 * Copyright 2017-2025 the original author or authors.
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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.config.EventSerializationConfigurationExtension;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Application configuration to register a Jackson-based {@link EventSerializer}.
 *
 * @author Oliver Drotbohm
 */
@Configuration(proxyBeanMethods = false)
class JacksonEventSerializationConfiguration implements EventSerializationConfigurationExtension {

	private final ObjectProvider<ObjectMapper> mapper;
	private final ApplicationContext context;

	/**
	 * Creates a new {@link JacksonEventSerializationConfiguration} for the given {@link ObjectMapper} and
	 * {@link ApplicationContext}.
	 *
	 * @param mapper must not be {@literal null}.
	 * @param context must not be {@literal null}.
	 */
	public JacksonEventSerializationConfiguration(ObjectProvider<ObjectMapper> mapper, ApplicationContext context) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");
		Assert.notNull(context, "ApplicationContext must not be null!");

		this.mapper = mapper;
		this.context = context;
	}

	@Bean
	public JacksonEventSerializer jacksonEventSerializer() {
		return new JacksonEventSerializer(() -> mapper.getIfAvailable(() -> defaultObjectMapper()));
	}

	private ObjectMapper defaultObjectMapper() {

		var mapper = new ObjectMapper();

		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		mapper.registerModules(context.getBeansOfType(Module.class).values());

		return mapper;
	}
}
