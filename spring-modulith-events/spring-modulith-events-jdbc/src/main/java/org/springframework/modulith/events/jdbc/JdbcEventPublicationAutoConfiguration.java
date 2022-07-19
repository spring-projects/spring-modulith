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
package org.springframework.modulith.events.jdbc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;

/**
 * @author Dmitry Belyaev, Björn Kieling
 */
@Configuration(proxyBeanMethods = false)
class JdbcEventPublicationAutoConfiguration implements EventPublicationConfigurationExtension {

    @Bean
    public JdbcEventPublicationRepository jpaEventPublicationRepository(
            JdbcTemplate jdbcTemplate, EventSerializer serializer) {
        // TODO Why do we want to instantiate the serializer here and what
        // happens if no serializer is available or is not compatible to
        // JDBC serialization?
        return new JdbcEventPublicationRepository(jdbcTemplate, serializer);
    }

    @Bean
    public DatabaseSchemaInitializer databaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        return new DatabaseSchemaInitializer(jdbcTemplate);
    }
}
