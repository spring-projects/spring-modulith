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

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.moduliths.moments.Quarter;
import org.moduliths.moments.support.Moments;
import org.moduliths.moments.support.MomentsProperties;
import org.moduliths.moments.support.TimeMachine;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests for {@link MomentsAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class MomentsAutoConfigurationTests {

	@Test
	void bootstrapsAutoConfiguration() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(MomentsAutoConfiguration.class))
				.run(it -> {
					assertThat(it).hasSingleBean(Moments.class)
							.doesNotHaveBean(TimeMachine.class);
				});
	}

	@Test
	void customizesTimeZone() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(MomentsAutoConfiguration.class))
				.withPropertyValues("moduliths.moments.zone-id:Europe/Berlin")
				.run(it -> {
					assertThat(it).getBean(MomentsProperties.class)
							.extracting(MomentsProperties::getZoneId)
							.isEqualTo(ZoneId.of("Europe/Berlin"));
				});
	}

	@Test
	void shiftsQuarterIfConfigured() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(MomentsAutoConfiguration.class))
				.withPropertyValues("moduliths.moments.quarter-start-month=February")
				.run(it -> {
					assertThat(it).getBean(MomentsProperties.class).satisfies(props -> {
						assertThat(props.getShiftedQuarter(LocalDate.of(2022, 1, 1)).getQuarter()).isEqualTo(Quarter.Q4);
					});
				});
	}

	@Test
	void doesNotRegisterMomentsBeanIfDisable() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(MomentsAutoConfiguration.class))
				.withPropertyValues("moduliths.moments.enabled=false")
				.run(it -> {
					assertThat(it).doesNotHaveBean(Moments.class);
					assertThat(it).doesNotHaveBean(MomentsProperties.class);
				});
	}

	@Test
	void exposesTimeMachineIfEnabledExplicitly() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(MomentsAutoConfiguration.class))
				.withPropertyValues("moduliths.moments.enable-time-machine=true")
				.run(it -> {
					assertThat(it).hasSingleBean(TimeMachine.class);
				});
	}
}
