package org.springframework.modulith.core;

import java.util.List;
import java.util.stream.Stream;

class CustomApplicationModuleDetectionStrategy implements ApplicationModuleDetectionStrategy {

	private static final List<String> NAMED_INTERFACE_PACKAGE_NAMES = List.of("mapper", "model", "repository", "service",
			"web");

	private static final List<String> INTERNAL_PACKAGE_NAME = List.of("internal");

	@Override
	public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {

		var allExclusions = Stream.concat(NAMED_INTERFACE_PACKAGE_NAMES.stream(), INTERNAL_PACKAGE_NAME.stream());

		// New method to be introduced on JavaPackage
		return basePackage.getSubPackagesMatching((pkg, trailingName) -> allExclusions.noneMatch(trailingName::contains));
	}

	@Override
	public NamedInterfaces detectNamedInterfaces(JavaPackage basePackage, ApplicationModuleInformation information) {

		return NamedInterfaces.builder(basePackage)
				.matching(NAMED_INTERFACE_PACKAGE_NAMES)
				.recursive()
				.build();
	}
}
