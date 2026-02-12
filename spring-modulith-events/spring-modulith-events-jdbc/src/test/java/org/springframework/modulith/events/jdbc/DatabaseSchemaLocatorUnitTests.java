/*
 * Copyright 2023-2026 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * Unit tests for {@link DatabaseSchemaInitializer}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSchemaLocatorUnitTests {

	@Mock ResourceLoader resourceLoader;

	@Test // GH-159
	void loadsSchemaFilesFromClasspath() {

		when(resourceLoader.getResource(any())).thenAnswer(it -> {
			return new ClassPathResource(it.<String> getArgument(0).substring(ResourceLoader.CLASSPATH_URL_PREFIX.length()));
		});

		var locator = new DatabaseSchemaLocator(resourceLoader);
		var captor = ArgumentCaptor.forClass(String.class);
		var settings = new JdbcRepositorySettings(DatabaseType.H2, CompletionMode.UPDATE,
				mock(JdbcConfigurationProperties.class));

		assertThatNoException().isThrownBy(() -> locator.getSchemaResource(settings));
		verify(resourceLoader).getResource(captor.capture());
		assertThat(captor.getValue()).startsWith(ResourceLoader.CLASSPATH_URL_PREFIX);
	}
}
