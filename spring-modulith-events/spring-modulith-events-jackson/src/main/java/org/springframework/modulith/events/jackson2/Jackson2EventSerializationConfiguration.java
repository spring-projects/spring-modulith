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
package org.springframework.modulith.events.jackson2;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
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
 * @since 2.0
 * @deprecated since 2.0, in favor of {@code JacksonEventSerializationConfiguration}.
 */
@Deprecated
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnMissingBean(EventSerializer.class)
class Jackson2EventSerializationConfiguration implements EventSerializationConfigurationExtension {

	private final ObjectProvider<ObjectMapper> mapper;
	private final ApplicationContext context;

	/**
	 * Creates a new {@link Jackson2EventSerializationConfiguration} for the given {@link ObjectMapper} and
	 * {@link ApplicationContext}.
	 *
	 * @param mapper must not be {@literal null}.
	 * @param context must not be {@literal null}.
	 */
	public Jackson2EventSerializationConfiguration(ObjectProvider<ObjectMapper> mapper, ApplicationContext context) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");
		Assert.notNull(context, "ApplicationContext must not be null!");

		this.mapper = mapper;
		this.context = context;
	}

	@Bean
	Jackson2EventSerializer jacksonEventSerializer() {
		return new Jackson2EventSerializer(() -> mapper.getIfAvailable(() -> defaultObjectMapper()));
	}

	private ObjectMapper defaultObjectMapper() {

		var mapper = new ObjectMapper();

		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		mapper.registerModules(context.getBeansOfType(Module.class).values());

		return mapper;
	}
}
