/*
 * Copyright 2017-2024 the original author or authors.
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
package org.springframework.modulith.events.config.restart;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.support.restart.ExclusiveIncompleteEventPublicationsProcessor;
import org.springframework.modulith.events.support.restart.IncompleteEventPublicationsProcessor;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * this configures a default Spring Integration lock delegating to a SQL database for cluster-wide locks.
 *
 * @author Josh Long
 */
@AutoConfiguration
@ConditionalOnClass({JdbcLockRegistry.class, JdbcTemplate.class, LockRegistry.class})
@ConditionalOnMissingBean(LockRegistry.class)
@ConditionalOnProperty(value = ExclusiveIncompleteEventPublicationsProcessorConfiguration.REPUBLISH_ON_RESTART_LOCK, matchIfMissing = false)
@ConditionalOnBean(DataSource.class)
@AutoConfigureBefore(DefaultIncompleteEventPublicationsProcessorConfiguration.class)
class ExclusiveIncompleteEventPublicationsProcessorConfiguration {

    static final String REPUBLISH_ON_RESTART_LOCK = "spring.modulith.events.republish-outstanding-events-on-restart.lock-name";
    static final String REPUBLISH_ON_RESTART_TIMEOUT_IN_MILLISECONDS = "spring.modulith.events.republish-outstanding-events-on-restart.lock-timeout";

    @Bean
    @ConditionalOnBean(DataSource.class)
    DefaultLockRepository defaultLockRepository(DataSource dataSource) {
        return new DefaultLockRepository(dataSource);
    }

    @Bean
    JdbcLockRegistry jdbcLockRegistry(LockRepository repository) {
        return new JdbcLockRegistry(repository);
    }

    @Bean
    @ConditionalOnMissingBean(IncompleteEventPublicationsProcessor.class)
    @ConditionalOnBean(IncompleteEventPublications.class)
    ExclusiveIncompleteEventPublicationsProcessor exclusiveIncompleteEventPublicationsProcessor(
            LockRegistry lockRegistry, Environment environment,
            IncompleteEventPublications publications) {
        var lockName = environment.getProperty(REPUBLISH_ON_RESTART_LOCK);
        var timeoutInMilliseconds = (long) environment.getProperty(
                REPUBLISH_ON_RESTART_TIMEOUT_IN_MILLISECONDS, Long.class, -1L);
        return new ExclusiveIncompleteEventPublicationsProcessor(StringUtils.hasText(lockName), lockName, timeoutInMilliseconds, publications, lockRegistry);
    }
}
