/*
 * Copyright 2022-2025 the original author or authors.
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
package org.springframework.modulith.events.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication;

/**
 * Auto-configuration for JPA based event publication. Registers this class' package as auto-configuration package, so
 * that it gets picked up for entity scanning by default.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
@AutoConfigureBefore({ HibernateJpaAutoConfiguration.class, EventPublicationAutoConfiguration.class })
@AutoConfigurationPackage(basePackageClasses = DefaultJpaEventPublication.class)
class JpaEventPublicationAutoConfiguration extends JpaEventPublicationConfiguration {}
