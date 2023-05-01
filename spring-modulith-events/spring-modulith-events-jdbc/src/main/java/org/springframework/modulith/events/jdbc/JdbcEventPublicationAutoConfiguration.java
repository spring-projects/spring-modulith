/*
 * Copyright 2022-2023 the original author or authors.
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
package org.springframework.modulith.events.jdbc;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;

/**
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
@Configuration(proxyBeanMethods = false)
class JdbcEventPublicationAutoConfiguration implements EventPublicationConfigurationExtension {

	@Bean
	DatabaseType databaseType(DataSource dataSource) {
		return DatabaseType.from(DatabaseDriver.fromDataSource(dataSource));
	}

	@Bean
	JdbcEventPublicationRepository jdbcEventPublicationRepository(JdbcTemplate jdbcTemplate,
			EventSerializer serializer, DatabaseType databaseType) {

		return new JdbcEventPublicationRepository(jdbcTemplate, serializer, databaseType);
	}

	@Bean
	@Conditional(SchemaInitializationEnabled.class)
	DatabaseSchemaInitializer databaseSchemaInitializer(JdbcTemplate jdbcTemplate, ResourceLoader resourceLoader,
			DatabaseType databaseType) {

		return new DatabaseSchemaInitializer(jdbcTemplate, resourceLoader, databaseType);
	}

	/**
	 * Combined condition to support the legacy schema initialization property as well as the new one.
	 *
	 * @author Oliver Drotbohm
	 */
	static class SchemaInitializationEnabled extends SpringBootCondition {

		private static final String LEGACY = "spring.modulith.events.jdbc-schema-initialization.enabled";
		private static final String CURRENT = "spring.modulith.events.jdbc.schema-initialization.enabled";

		/*
		 * (non-Javadoc)
		 * @see org.springframework.boot.autoconfigure.condition.SpringBootCondition#getMatchOutcome(org.springframework.context.annotation.ConditionContext, org.springframework.core.type.AnnotatedTypeMetadata)
		 */
		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {

			Environment environment = context.getEnvironment();

			var enabled = List.of(CURRENT, LEGACY).stream()
					.map(it -> environment.getProperty(it, Boolean.class))
					.anyMatch(Boolean.TRUE::equals);

			return enabled //
					? ConditionOutcome.match("Schema initialization explicitly enabled.") //
					: ConditionOutcome.noMatch("Schema initialization disabled by default.");
		}
	}
}
