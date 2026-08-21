/*
 * Copyright 2026 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import tools.jackson.databind.json.JsonMapper;

/**
 * @author Dongliang Xie
 */
class RabbitJacksonConfigurationIntegrationTests {

	@Test // GH-1776
	void prefersJackson3WhenBothMappersAreAvailable() {

		createRunner()
				.withBean(ObjectMapper.class, ObjectMapper::new)
				.withBean(JsonMapper.class, JsonMapper::new)
				.run(context -> {

					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(RabbitTemplateCustomizer.class);

					var template = new RabbitTemplate();
					context.getBean(RabbitTemplateCustomizer.class).customize(template);

					assertThat(template.getMessageConverter()).isInstanceOf(JacksonJsonMessageConverter.class);
				});
	}

	@Test
	void fallsBackToJackson2WhenJackson3IsNotAvailable() {

		createRunner()
				.withClassLoader(new FilteredClassLoader("tools.jackson"))
				.withBean(ObjectMapper.class, ObjectMapper::new)
				.run(context -> {

					assertThat(context).hasSingleBean(RabbitTemplateCustomizer.class);

					var template = new RabbitTemplate();
					context.getBean(RabbitTemplateCustomizer.class).customize(template);

					assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
				});
	}

	@Test
	void keepsAdditionalCustomizerWhenFallingBackToJackson2() {

		createRunner()
				.withClassLoader(new FilteredClassLoader("tools.jackson"))
				.withBean(ObjectMapper.class, ObjectMapper::new)
				.withBean("customRabbitTemplateCustomizer", RabbitTemplateCustomizer.class, () -> template -> {})
				.run(context -> assertThat(context.getBeansOfType(RabbitTemplateCustomizer.class))
						.containsOnlyKeys("rabbitTemplateCustomizer", "customRabbitTemplateCustomizer"));
	}

	@Test
	void backsOffForUserDefinedCustomizer() {

		RabbitTemplateCustomizer customizer = template -> {};

		createRunner()
				.withBean(ObjectMapper.class, ObjectMapper::new)
				.withBean(JsonMapper.class, JsonMapper::new)
				.withBean("rabbitTemplateCustomizer", RabbitTemplateCustomizer.class, () -> customizer)
				.run(context -> {

					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(RabbitTemplateCustomizer.class);
					assertThat(context.getBean(RabbitTemplateCustomizer.class)).isSameAs(customizer);
				});
	}

	private static ApplicationContextRunner createRunner() {
		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(RabbitJackson2Configuration.class,
						RabbitJacksonConfiguration.class));
	}
}
