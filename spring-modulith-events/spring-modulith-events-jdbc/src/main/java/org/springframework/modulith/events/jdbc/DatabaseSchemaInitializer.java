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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Initializes the DB schema used to store events
 *
 * @author Dmitry Belyaev, Björn Kieling
 */
public class DatabaseSchemaInitializer implements ResourceLoaderAware, InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    private ResourceLoader resourceLoader;

    @Value("${spring.modulith.events.schema-initialization.enabled:false}")
    private boolean initEnabled;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void afterPropertiesSet() throws MetaDataAccessException {
        if (!initEnabled) {
            return;
        }

        DatabaseType databaseType = DatabaseType.fromMetaData(jdbcTemplate.getDataSource());
        String databaseName = databaseType.name().toLowerCase();
        var schemaDdlResource = resourceLoader.getResource("/schema-" + databaseName + ".sql");
        var schemaDdl = asString(schemaDdlResource);
        jdbcTemplate.execute(schemaDdl);
    }

    private String asString(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
