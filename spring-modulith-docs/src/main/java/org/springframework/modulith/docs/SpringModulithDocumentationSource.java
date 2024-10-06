/*
 * Copyright 2024 the original author or authors.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.json.BasicJsonParser;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.modulith.docs.metadata.MethodMetadata;
import org.springframework.modulith.docs.metadata.TypeMetadata;
import org.springframework.modulith.docs.util.BuildSystemUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * A {@link DocumentationSource} using metadata found in {@value #METADATA_FILE}, usually generated via
 * {@code spring-modulith-apt}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class SpringModulithDocumentationSource implements DocumentationSource {

	private static final String METADATA_FILE = "generated-spring-modulith/javadoc.json";
	private static final Optional<DocumentationSource> INSTANCE = BuildSystemUtils
			.getTargetResource(METADATA_FILE).map(SpringModulithDocumentationSource::new);

	private Collection<TypeMetadata> metadata;

	/**
	 * Creates a new {@link SpringModulithDocumentationSource} for the given file.
	 *
	 * @param resource must not be {@literal null}.
	 */
	private SpringModulithDocumentationSource(Resource resource) {

		Assert.notNull(resource, "Resource must not be null!");

		this.metadata = from(resource);
	}

	/**
	 * Creates a new {@link DocumentationSource} if the backing metadata file (in {@value #METADATA_FILE}) is present.
	 *
	 * @return will never be {@literal null}.
	 */
	public static Optional<DocumentationSource> getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the location of the metadata file.
	 *
	 * @return will never be {@literal null}.
	 */
	public static String getMetadataLocation() {
		return METADATA_FILE;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.docs.DocumentationSource#getDocumentation(com.tngtech.archunit.core.domain.JavaClass)
	 */
	@Override
	public Optional<String> getDocumentation(JavaClass type) {

		return metadata.stream()
				.filter(it -> it.name().equals(type.getName()))
				.findFirst()
				.map(TypeMetadata::comment)
				.filter(StringUtils::hasText);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.docs.DocumentationSource#getDocumentation(com.tngtech.archunit.core.domain.JavaMethod)
	 */
	@Override
	public Optional<String> getDocumentation(JavaMethod method) {

		var owner = method.getOwner();

		return metadata.stream()
				.filter(it -> it.name().equals(owner.getName()))
				.findFirst()
				.stream()
				.flatMap(it -> it.methods().stream())
				.filter(it -> it.hasSignatureOf(method.reflect()))
				.findFirst()
				.map(MethodMetadata::comment)
				.filter(StringUtils::hasText);
	}

	@SuppressWarnings("unchecked")
	private static Collection<TypeMetadata> from(Resource resource) {

		try {

			var content = resource.getContentAsString(StandardCharsets.UTF_8);
			var parsed = new BasicJsonParser().parseList(content);

			return parsed.stream()
					.map(it -> it instanceof TypeMetadata metadata ? metadata : typeMetadata((Map<String, Object>) it))
					.toList();

		} catch (IOException o_O) {
			throw new RuntimeException(o_O);
		}
	}

	@SuppressWarnings("unchecked")
	private static TypeMetadata typeMetadata(Map<String, Object> source) {

		var methods = source.containsKey("methods")
				? ((List<Map<String, Object>>) source.get("methods")).stream()
						.map(SpringModulithDocumentationSource::methodMetadata)
						.toList()
				: Collections.<MethodMetadata> emptyList();

		return new TypeMetadata(
				source.get("name").toString(),
				getString(source, "comment"),
				methods);
	}

	private static MethodMetadata methodMetadata(Map<String, Object> source) {

		return new MethodMetadata(
				source.get("name").toString(),
				source.get("signature").toString(),
				getString(source, "comment"));
	}

	@Nullable
	private static String getString(Map<String, Object> source, String key) {

		Object result = source.get(key);

		return result == null ? null : result.toString();
	}
}
