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
package org.springframework.modulith.events.config;

import static org.springframework.core.io.support.SpringFactoriesLoader.*;

import lombok.RequiredArgsConstructor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.modulith.events.config.EnablePersistentDomainEvents.PersistentDomainEventsImportSelector;

/**
 * @author Oliver Gierke
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(PersistentDomainEventsImportSelector.class)
public @interface EnablePersistentDomainEvents {

	@RequiredArgsConstructor
	static class PersistentDomainEventsImportSelector implements ImportSelector, ResourceLoaderAware {

		private ResourceLoader resourceLoader;

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.context.ResourceLoaderAware#setResourceLoader(org.springframework.core.io.ResourceLoader)
		 */
		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.context.annotation.ImportSelector#selectImports(org.springframework.core.type.AnnotationMetadata)
		 */
		@Override
		public String[] selectImports(AnnotationMetadata importingClassMetadata) {

			List<String> result = new ArrayList<>();

			result.add(EventPublicationConfiguration.class.getName());
			result.addAll(loadFactoryNames(EventPublicationConfigurationExtension.class, resourceLoader.getClassLoader()));
			result.addAll(loadFactoryNames(EventSerializationConfigurationExtension.class, resourceLoader.getClassLoader()));

			return result.toArray(new String[result.size()]);
		}
	}
}
