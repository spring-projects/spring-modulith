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
package org.springframework.modulith.testapp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.jdbc.DatabaseSchemaInitializer;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author Dmitry Belyaev, Björn Kieling
 */
public class DatabaseSchemaInitializerTest {

    @Nested
    @DataJdbcTest(properties = {
            "spring.modulith.events.schema-initialization.enabled=true"
    })
    @Import(DatabaseSchemaInitializer.class)
    class InitializationEnabled {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        public void shouldCreateDatabaseSchemaOnStartUp() {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION", Long.class);

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DataJdbcTest(properties = {
            "spring.modulith.events.schema-initialization.enabled=false"
    })
    @Import(DatabaseSchemaInitializer.class)
    class InitializationDisabled {

        @SpyBean
        private JdbcTemplate jdbcTemplate;

        @Test
        public void shouldNotCreateDatabaseSchemaOnStartUp() {
            Mockito.verify(jdbcTemplate, Mockito.never()).execute(anyString());
        }
    }

    @Nested
    @DataJdbcTest
    @Import(DatabaseSchemaInitializer.class)
    class InitializationDisabledByDefault {

        @SpyBean
        private JdbcTemplate jdbcTemplate;

        @Test
        public void shouldNotCreateDatabaseSchemaOnStartUp() {
            Mockito.verify(jdbcTemplate, Mockito.never()).execute(anyString());
        }
    }

    @Nested
    @DataJdbcTest(properties = {
            "spring.modulith.events.schema-initialization.enabled=true"
    })
    @ActiveProfiles("hsql")
    @Import(DatabaseSchemaInitializer.class)
    class InitializationUseHsql {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        public void shouldCreateDatabaseSchemaOnStartUp() {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION", Long.class);

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DataJdbcTest(properties = {
            "spring.modulith.events.schema-initialization.enabled=true"
    })
    @ActiveProfiles("h2")
    @Import(DatabaseSchemaInitializer.class)
    class InitializationUseH2 {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        public void shouldCreateDatabaseSchemaOnStartUp() {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION", Long.class);

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @Disabled
    @DataJdbcTest(properties = {
            "spring.modulith.events.schema-initialization.enabled=true"
    })
    @ActiveProfiles("postgres")
    @Import(DatabaseSchemaInitializer.class)
    class InitializationUsePostgres {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        public void shouldCreateDatabaseSchemaOnStartUp() {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION", Long.class);

            assertThat(count).isEqualTo(0);
        }
    }
}
