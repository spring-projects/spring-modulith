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
package org.springframework.modulith.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.model.NamedInterface.TypeBasedNamedInterface;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class NamedInterfaces implements Iterable<NamedInterface> {

	public static final NamedInterfaces NONE = new NamedInterfaces(Collections.emptyList());

	private final List<NamedInterface> namedInterfaces;

	public static NamedInterfaces discoverNamedInterfaces(JavaPackage basePackage) {

		return NamedInterfaces.ofAnnotatedPackages(basePackage) //
				.and(NamedInterfaces.ofAnnotatedTypes(basePackage)) //
				.and(NamedInterface.unnamed(basePackage));
	}

	public static NamedInterfaces of(List<NamedInterface> interfaces) {
		return interfaces.isEmpty() ? NONE : new NamedInterfaces(interfaces);
	}

	static NamedInterfaces ofAnnotatedPackages(JavaPackage basePackage) {

		return basePackage //
				.getSubPackagesAnnotatedWith(org.springframework.modulith.NamedInterface.class) //
				.flatMap(it -> NamedInterface.of(it).stream()) //
				.collect(Collectors.collectingAndThen(Collectors.toList(), NamedInterfaces::of));
	}

	private static List<TypeBasedNamedInterface> ofAnnotatedTypes(JavaPackage basePackage) {

		MultiValueMap<String, JavaClass> mappings = new LinkedMultiValueMap<>();

		basePackage.stream() //
				.filter(it -> !JavaPackage.isPackageInfoType(it)) //
				.forEach(it -> {

					if (!it.isAnnotatedWith(org.springframework.modulith.NamedInterface.class)) {
						return;
					}

					org.springframework.modulith.NamedInterface annotation = AnnotatedElementUtils
							.getMergedAnnotation(it.reflect(), org.springframework.modulith.NamedInterface.class);

					for (String name : annotation.name()) {
						mappings.add(name, it);
					}
				});

		return mappings.entrySet().stream() //
				.map(entry -> NamedInterface.of(entry.getKey(), Classes.of(entry.getValue()), basePackage)) //
				.toList();
	}

	private NamedInterfaces and(NamedInterface namedInterface) {

		List<NamedInterface> result = new ArrayList<>(namedInterfaces.size() + 1);
		result.addAll(namedInterfaces);
		result.add(namedInterface);

		return new NamedInterfaces(result);
	}

	public boolean hasExplicitInterfaces() {
		return namedInterfaces.size() > 1 || !namedInterfaces.get(0).isUnnamed();
	}

	public Stream<NamedInterface> stream() {
		return namedInterfaces.stream();
	}

	public NamedInterfaces and(List<TypeBasedNamedInterface> others) {

		List<NamedInterface> namedInterfaces = new ArrayList<>();
		List<NamedInterface> unmergedInterface = this.namedInterfaces;

		for (TypeBasedNamedInterface candidate : others) {

			Optional<NamedInterface> existing = namedInterfaces.stream() //
					.filter(it -> it.hasSameNameAs(candidate)) //
					.findFirst();

			// Merge existing with new and add to result
			existing.ifPresent(it -> {
				namedInterfaces.add(it.merge(candidate));
				namedInterfaces.add(it);
				unmergedInterface.remove(it);
			});

			// Simply add candidate
			if (!existing.isPresent()) {
				namedInterfaces.add(candidate);
			}
		}

		namedInterfaces.addAll(unmergedInterface);

		return new NamedInterfaces(namedInterfaces);
	}

	public Optional<NamedInterface> getByName(String name) {
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
}
