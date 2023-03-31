/*
 * Copyright 2018-2023 the original author or authors.
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
package org.springframework.modulith.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * A collection of {@link NamedInterface}s.
 *
 * @author Oliver Drotbohm
 */
public class NamedInterfaces implements Iterable<NamedInterface> {

	public static final NamedInterfaces NONE = new NamedInterfaces(Collections.emptyList());

	private final List<NamedInterface> namedInterfaces;

	/**
	 * Creates a new {@link NamedInterfaces} for all {@link NamedInterface}s.
	 *
	 * @param namedInterfaces must not be {@literal null}.
	 */
	private NamedInterfaces(List<NamedInterface> namedInterfaces) {

		Assert.notNull(namedInterfaces, "Named interfaces must not be null!");

		this.namedInterfaces = namedInterfaces;
	}

	/**
	 * Discovers all {@link NamedInterfaces} declared for the given {@link JavaPackage}.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterfaces discoverNamedInterfaces(JavaPackage basePackage) {

		return NamedInterfaces.ofAnnotatedPackages(basePackage) //
				.and(NamedInterfaces.ofAnnotatedTypes(basePackage)) //
				.and(NamedInterface.unnamed(basePackage));
	}

	/**
	 * Creates a new {@link NamedInterfaces} for the given {@link NamedInterface}s.
	 *
	 * @param interfaces must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterfaces of(List<NamedInterface> interfaces) {
		return interfaces.isEmpty() ? NONE : new NamedInterfaces(interfaces);
	}

	/**
	 * Creates a new {@link NamedInterfaces} for the given base {@link JavaPackage}.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterfaces ofAnnotatedPackages(JavaPackage basePackage) {

		Assert.notNull(basePackage, "Base package must not be null!");

		return basePackage //
				.getSubPackagesAnnotatedWith(org.springframework.modulith.NamedInterface.class) //
				.flatMap(it -> NamedInterface.of(it).stream()) //
				.collect(Collectors.collectingAndThen(Collectors.toList(), NamedInterfaces::of));
	}

	/**
	 * Returns whether at least one explicit {@link NamedInterface} is declared.
	 *
	 * @return will never be {@literal null}.
	 */
	public boolean hasExplicitInterfaces() {
		return namedInterfaces.size() > 1 || !namedInterfaces.get(0).isUnnamed();
	}

	/**
	 * Create a {@link Stream} of {@link NamedInterface}s.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<NamedInterface> stream() {
		return namedInterfaces.stream();
	}

	/**
	 * Returns the {@link NamedInterface} with the given name if present.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Optional<NamedInterface> getByName(String name) {

		Assert.hasText(name, "Named interface name must not be null or empty!");

		return namedInterfaces.stream().filter(it -> it.getName().equals(name)).findFirst();
	}

	/**
	 * Returns the unnamed {@link NamedInterface} of the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public NamedInterface getUnnamedInterface() {

		return namedInterfaces.stream() //
				.filter(NamedInterface::isUnnamed) //
				.findFirst() //
				.orElseThrow(() -> new IllegalStateException("No unnamed interface found!"));
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<NamedInterface> iterator() {
		return namedInterfaces.iterator();
	}

	/**
	 * Creates a new {@link NamedInterfaces} instance with the given {@link TypeBasedNamedInterface}s added.
	 *
	 * @param others must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	NamedInterfaces and(List<NamedInterface> others) {

		Assert.notNull(others, "Other TypeBasedNamedInterfaces must not be null!");

		var namedInterfaces = new ArrayList<NamedInterface>();
		var unmergedInterface = this.namedInterfaces;

		if (others.isEmpty()) {
			return this;
		}

		for (NamedInterface candidate : others) {

			var existing = this.namedInterfaces.stream() //
					.filter(candidate::hasSameNameAs) //
					.findFirst();

			// Merge existing with new and add to result
			existing.ifPresentOrElse(it -> {
				namedInterfaces.add(it.merge(candidate));
				unmergedInterface.remove(it);
			},
					() -> namedInterfaces.add(candidate));
		}

		namedInterfaces.addAll(unmergedInterface);

		return new NamedInterfaces(namedInterfaces);
	}

	private NamedInterfaces and(NamedInterface namedInterface) {

		var result = new ArrayList<NamedInterface>(namedInterfaces.size() + 1);
		result.addAll(namedInterfaces);
		result.add(namedInterface);

		return new NamedInterfaces(result);
	}

	private static List<NamedInterface> ofAnnotatedTypes(JavaPackage basePackage) {

		var mappings = new LinkedMultiValueMap<String, JavaClass>();

		basePackage.stream() //
				.filter(it -> !JavaPackage.isPackageInfoType(it)) //
				.forEach(it -> {

					if (!it.isAnnotatedWith(org.springframework.modulith.NamedInterface.class)) {
						return;
					}

					var annotation = AnnotatedElementUtils.getMergedAnnotation(it.reflect(),
							org.springframework.modulith.NamedInterface.class);

					NamedInterface.getDefaultedNames(annotation, it.getPackageName())
							.forEach(name -> mappings.add(name, it));
				});

		return mappings.entrySet().stream() //
				.map(entry -> NamedInterface.of(entry.getKey(), Classes.of(entry.getValue()))) //
				.toList();
	}
}
