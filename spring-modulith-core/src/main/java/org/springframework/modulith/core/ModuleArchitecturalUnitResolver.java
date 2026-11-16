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
package org.springframework.modulith.core;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jmolecules.archunit.ArchitecturalUnitResolver;
import org.jmolecules.archunit.ArchitecturalUnits.ArchitecturalUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * An {@link ArchitecturalUnitResolver} treating the {@link ApplicationModule}s of an {@link ApplicationModules}
 * instance as jMolecules architectural units, no matter how they were detected (explicit annotation or implicitly as
 * direct sub-package of the application package). Nested modules are separate units, and a type belongs to the one
 * with the most specific base package. Can be restricted to the modules whose base package carries a particular
 * annotation, see {@link #ofAnnotated(ApplicationModules, String)}. Requires the jMolecules ArchUnit integration on the
 * classpath.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 * @see HexagonalVerifications
 * @see OnionVerifications
 */
class ModuleArchitecturalUnitResolver implements ArchitecturalUnitResolver {

	private final ApplicationModules modules;
	private final Predicate<ApplicationModule> filter;
	private final Map<ApplicationModuleIdentifier, ArchitecturalUnit> units = new ConcurrentHashMap<>();

	private ModuleArchitecturalUnitResolver(ApplicationModules modules, Predicate<ApplicationModule> filter) {

		this.modules = modules;
		this.filter = filter;
	}

	/**
	 * Creates a new {@link ModuleArchitecturalUnitResolver} for the given {@link ApplicationModules}.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static ModuleArchitecturalUnitResolver of(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		return new ModuleArchitecturalUnitResolver(modules, __ -> true);
	}

	/**
	 * Creates a new {@link ModuleArchitecturalUnitResolver} for the given {@link ApplicationModules}, only considering
	 * the modules whose base package is (meta-)annotated with the given annotation as units. The types of other modules
	 * belong to the unit of the closest enclosing module that is, or to none at all.
	 *
	 * @param modules must not be {@literal null}.
	 * @param annotation the fully qualified name of the annotation, must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	static ModuleArchitecturalUnitResolver ofAnnotated(ApplicationModules modules, String annotation) {

		Assert.notNull(modules, "ApplicationModules must not be null!");
		Assert.hasText(annotation, "Annotation must not be null or empty!");

		Map<ApplicationModuleIdentifier, Boolean> annotated = new ConcurrentHashMap<>();
		Predicate<ApplicationModule> filter = module -> annotated.computeIfAbsent(module.getIdentifier(), __ -> {

			var pkg = getArchUnitPackage(module);

			return pkg.isAnnotatedWith(annotation) || pkg.isMetaAnnotatedWith(annotation);
		});

		return new ModuleArchitecturalUnitResolver(modules, filter);
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.archunit.ArchitecturalUnitResolver#getUnitFor(com.tngtech.archunit.core.domain.JavaClass)
	 */
	@Override
	public @Nullable ArchitecturalUnit getUnitFor(JavaClass type) {

		Assert.notNull(type, "JavaClass must not be null!");

		return modules.stream()
				.filter(it -> it.contains(type))
				.filter(filter)
				.max(Comparator.comparingInt((ApplicationModule it) -> it.getBasePackage().getName().length()))
				.map(this::getUnit)
				.orElse(null);
	}

	private ArchitecturalUnit getUnit(ApplicationModule module) {

		var existing = units.get(module.getIdentifier());

		if (existing != null) {
			return existing;
		}

		// Resolve the parent outside of computeIfAbsent(…) as ConcurrentHashMap doesn't support recursive updates
		var parent = getParentUnitModule(module).map(this::getUnit).orElse(null);

		return units.computeIfAbsent(module.getIdentifier(), __ -> ArchitecturalUnit.of(module.getIdentifier().toString(),
				module.getDisplayName(), null, getArchUnitPackage(module), parent));
	}

	/**
	 * Returns the closest module enclosing the given one that is considered a unit.
	 */
	private Optional<ApplicationModule> getParentUnitModule(ApplicationModule module) {

		var parent = modules.getParentOf(module);

		while (parent.isPresent() && !filter.test(parent.get())) {
			parent = modules.getParentOf(parent.get());
		}

		return parent;
	}

	/**
	 * Looks up the ArchUnit package of the module's base package by walking up from the package of any of its types.
	 */
	private static com.tngtech.archunit.core.domain.JavaPackage getArchUnitPackage(ApplicationModule module) {

		var basePackageName = module.getBasePackage().getName();
		var pkg = module.getBasePackage().stream()
				.findFirst()
				.map(JavaClass::getPackage)
				.orElseThrow(() -> new IllegalStateException("Module %s does not contain any types!".formatted(module)));

		while (!pkg.getName().equals(basePackageName)) {

			pkg = pkg.getParent().orElseThrow(() -> new IllegalStateException(
					"Could not find package %s of module %s!".formatted(basePackageName, module)));
		}

		return pkg;
	}
}
