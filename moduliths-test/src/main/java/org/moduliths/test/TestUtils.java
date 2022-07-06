/*
 * Copyright 2018 the original author or authors.
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
package org.moduliths.test;

import static org.assertj.core.api.Assertions.*;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.BootstrapContext;
import org.springframework.test.context.CacheAwareContextLoaderDelegate;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate;
import org.springframework.test.context.support.DefaultBootstrapContext;

/**
 * @author Oliver Gierke
 */
public class TestUtils {

	public static void assertDependencyMissing(Class<?> testClass, Class<?> expectedMissingDependency) {

		CacheAwareContextLoaderDelegate delegate = new DefaultCacheAwareContextLoaderDelegate();
		BootstrapContext bootstrapContext = new DefaultBootstrapContext(testClass, delegate);

		SpringBootTestContextBootstrapper bootstrapper = new SpringBootTestContextBootstrapper();
		bootstrapper.setBootstrapContext(bootstrapContext);

		MergedContextConfiguration configuration = bootstrapper.buildMergedContextConfiguration();

		AssertableApplicationContext context = AssertableApplicationContext.get(() -> {

			SpringBootContextLoader loader = new SpringBootContextLoader();

			try {

				return (ConfigurableApplicationContext) loader.loadContext(configuration);

			} catch (Exception e) {

				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				}

				throw new RuntimeException(e);
			}
		});

		assertThat(context).hasFailed();

		assertThat(context).getFailure().isInstanceOfSatisfying(UnsatisfiedDependencyException.class, it -> {
			assertThat(it.getMostSpecificCause()).isInstanceOfSatisfying(NoSuchBeanDefinitionException.class, ex -> {
				assertThat(ex.getBeanType()).isEqualTo(expectedMissingDependency);
			});
		});
	}
}
