/*
 * Copyright 2022-2024 the original author or authors.
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

import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 * @author Raed Ben Hamouda
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(EventPublicationAutoConfiguration.class)
@EnableConfigurationProperties(JdbcConfigurationProperties.class)
class JdbcEventPublicationAutoConfiguration implements EventPublicationConfigurationExtension {

	@Bean
	DatabaseType databaseType(DataSource dataSource) {
		return DatabaseType.from(fromDataSource(dataSource));
	}

	@Bean
	JdbcRepositorySettings jdbcEventPublicationRepositorySettings(DatabaseType databaseType,
			JdbcConfigurationProperties properties, Environment environment) {

		return new JdbcRepositorySettings(databaseType, CompletionMode.from(environment), properties.getSchema());
	}

	@Bean
	JdbcEventPublicationRepository jdbcEventPublicationRepository(JdbcTemplate jdbcTemplate,
			EventSerializer serializer, JdbcRepositorySettings settings) {
		return new JdbcEventPublicationRepository(jdbcTemplate, serializer, settings);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.modulith.events.jdbc.schema-initialization.enabled", havingValue = "true")
	DatabaseSchemaInitializer databaseSchemaInitializer(DataSource dataSource, ResourceLoader resourceLoader,
			DatabaseType databaseType, JdbcTemplate jdbcTemplate, JdbcConfigurationProperties properties) {

		return new DatabaseSchemaInitializer(dataSource, resourceLoader, databaseType, jdbcTemplate, properties);
	}

	private static String fromDataSource(DataSource dataSource) {

		String name = null;

		try {

			var metadata = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
			name = JdbcUtils.commonDatabaseName(metadata);

		} catch (Exception o_O) {}

		return name == null ? "UNKNOWN" : name;
	}
}
