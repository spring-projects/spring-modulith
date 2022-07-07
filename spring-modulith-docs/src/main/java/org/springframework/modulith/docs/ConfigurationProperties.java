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
package org.springframework.modulith.docs;

import lombok.Value;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.docs.ConfigurationProperties.ConfigurationProperty;
import org.springframework.modulith.model.Module;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.tngtech.archunit.core.domain.JavaType;

/**
 * Represents all {@link ConfigurationProperty} instances found for the current project.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class ConfigurationProperties implements Iterable<ConfigurationProperty> {

	private static final String METADATA_PATH = "classpath:META-INF/spring-configuration-metadata.json";
	private static final JsonPath PATH = JsonPath.compile("$.properties");

	private final List<ConfigurationProperty> properties;

	/**
	 * Creates a new {@link ConfigurationProperties} instance.
	 */
	ConfigurationProperties() {

		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

		try {
			Resource[] resources = resolver.getResources(METADATA_PATH);

			this.properties = Arrays.stream(resources)
					.flatMap(ConfigurationProperties::parseProperties)
					.collect(Collectors.toList());

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns all {@link ModuleProperty} instances for the given {@link Module}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	public List<ModuleProperty> getModuleProperties(Module module) {

		Assert.notNull(module, "Module must not be null!");

		return properties.stream()
				.flatMap(it -> getModuleProperty(module, it))
				.collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ConfigurationProperty> iterator() {
		return properties.iterator();
	}

	private Stream<ModuleProperty> getModuleProperty(Module module,
			ConfigurationProperty property) {

		return module.getType(property.getSourceType())
				.map(it -> new ModuleProperty(property.getName(), property.getDescription(), property.getType(), it,
						property.getDefaultValue()))
				.map(Stream::of)
				.orElseGet(Stream::empty);
	}

	@SuppressWarnings("unchecked")
	private static Stream<ConfigurationProperty> parseProperties(Resource source) {

		if (!source.exists()) {
			return Stream.empty();
		}

		try (InputStream stream = source.getInputStream()) {

			DocumentContext context = JsonPath.parse(stream);
			List<Object> read = context.read(PATH, List.class);

			return read.stream()
					.map(it -> (Map<String, Object>) it)
					.flatMap(ConfigurationProperty::of);

		} catch (Exception o_O) {
			return Stream.empty();
		}
	}

	@Value
	static class ConfigurationProperty {

		String name;
		@Nullable String description;
		String type, sourceType;
		@Nullable String defaultValue;

		@SuppressWarnings("null")
		static Stream<ConfigurationProperty> of(Map<String, Object> source) {

			String sourceType = getAsString(source, "sourceType");

			if (!StringUtils.hasText(sourceType)) {
				return Stream.empty();
			}

			ConfigurationProperty property = new ConfigurationProperty(getAsString(source, "name"),
					getAsString(source, "description"),
					getAsString(source, "type"),
					sourceType,
					getAsString(source, "defaultValue"));

			return Stream.of(property);
		}

		boolean hasSourceType() {
			return StringUtils.hasText(sourceType);
		}

		private static @Nullable String getAsString(Map<String, Object> source, String key) {

			Object value = source.get(key);

			return value == null ? null : value.toString();
		}
	}

	@Value
	static class ModuleProperty {
		String name;
		@Nullable String description;
		String type;
		JavaType sourceType;
		@Nullable String defaultValue;
	}
}
