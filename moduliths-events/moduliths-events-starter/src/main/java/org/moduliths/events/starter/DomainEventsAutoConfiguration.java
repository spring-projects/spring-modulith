/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moduliths.events.starter;

import org.moduliths.events.EventPublication;
import org.moduliths.events.config.EnablePersistentDomainEvents;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Enables auto-configuration for the events package.
 * 
 * @author Oliver Gierke
 */
@Configuration
@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
@Import(DomainEventsAutoConfiguration.EnableAutoConfigForEventsPackage.class)
@EnablePersistentDomainEvents
public class DomainEventsAutoConfiguration {

	static class EnableAutoConfigForEventsPackage implements ImportBeanDefinitionRegistrar {

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.context.annotation.ImportBeanDefinitionRegistrar#registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata, org.springframework.beans.factory.support.BeanDefinitionRegistry)
		 */
		@Override
		public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
			AutoConfigurationPackages.register(registry, EventPublication.class.getPackage().getName());
		}
	}
}
