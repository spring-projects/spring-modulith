/*
 * Copyright 2022 the original author or authors.
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
package org.moduliths.moments.autoconfigure;

import java.time.Clock;

import org.moduliths.moments.support.Moments;
import org.moduliths.moments.support.MomentsProperties;
import org.moduliths.moments.support.TimeMachine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for {@link Moments}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
@EnableScheduling
@EnableConfigurationProperties(MomentsProperties.class)
@ConditionalOnProperty(name = "moduliths.moments.enabled", havingValue = "true", matchIfMissing = true)
@Configuration(proxyBeanMethods = false)
class MomentsAutoConfiguration {

	@Bean
	@ConditionalOnProperty(name = "moduliths.moments.enable-time-machine", havingValue = "false", matchIfMissing = true)
	Moments moments(ObjectProvider<Clock> clockProvider, ApplicationEventPublisher events, MomentsProperties properties) {

		Clock clock = clockProvider.getIfAvailable(() -> Clock.systemUTC());

		return new Moments(clock, events, properties);
	}

	@Bean
	@ConditionalOnProperty(name = "moduliths.moments.enable-time-machine", havingValue = "true", matchIfMissing = false)
	TimeMachine timeMachine(ObjectProvider<Clock> clockProvider, ApplicationEventPublisher events,
			MomentsProperties properties) {

		Clock clock = clockProvider.getIfAvailable(() -> Clock.systemUTC());

		return new TimeMachine(clock, events, properties);
	}
}
