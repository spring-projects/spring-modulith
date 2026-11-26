/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.events;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Integration tests for the executor selection of {@link ApplicationModuleListener} in a plain Spring application.
 *
 * @author Hyun Lee
 */
class ApplicationModuleListenerIntegrationTests {

	@Test // GH-641
	void runsListenerOnDedicatedTaskExecutorIfPresent() throws Exception {
		assertThat(invokeListener(DedicatedExecutorConfiguration.class).getName()).startsWith("dedicated-");
	}

	@Test // GH-641
	void fallsBackToDefaultTaskExecutorIfNoDedicatedOneIsPresent() throws Exception {
		assertThat(invokeListener(DefaultExecutorConfiguration.class).getName()).startsWith("default-");
	}

	private static Thread invokeListener(Class<?> configuration) throws Exception {

		try (var context = new AnnotationConfigApplicationContext(configuration)) {

			var thread = new CompletableFuture<Thread>();

			context.getBean(SampleListener.class).on(thread);

			return thread.get(5, TimeUnit.SECONDS);
		}
	}

	private static ThreadPoolTaskExecutor executor(String threadNamePrefix) {

		var executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix(threadNamePrefix);

		return executor;
	}

	@EnableAsync
	@Configuration(proxyBeanMethods = false)
	static class DefaultExecutorConfiguration {

		@Bean
		SampleListener listener() {
			return new SampleListener();
		}

		@Bean
		ThreadPoolTaskExecutor taskExecutor() {
			return executor("default-");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Import(DefaultExecutorConfiguration.class)
	static class DedicatedExecutorConfiguration {

		@Bean(ApplicationModuleListener.TASK_EXECUTOR_BEAN_NAME)
		ThreadPoolTaskExecutor applicationModuleListenerTaskExecutor() {
			return executor("dedicated-");
		}
	}

	static class SampleListener {

		@ApplicationModuleListener
		void on(CompletableFuture<Thread> thread) {
			thread.complete(Thread.currentThread());
		}
	}
}
