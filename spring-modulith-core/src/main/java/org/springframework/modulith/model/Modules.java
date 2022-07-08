/*
 * Copyright 2018-2020 the original author or authors.
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
package org.springframework.modulith.model;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static java.util.stream.Collectors.*;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.With;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jmolecules.archunit.JMoleculesDddRules;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.modulith.Modulith;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.model.Types.JMoleculesTypes;
import org.springframework.util.Assert;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.FailureReport;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * @author Oliver Gierke
 * @author Peter Gafert
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Modules implements Iterable<Module> {

	private static final Map<CacheKey, Modules> CACHE = new HashMap<>();

	private static final ModuleDetectionStrategy DETECTION_STRATEGY;

	static {

		List<ModuleDetectionStrategy> loadFactories = SpringFactoriesLoader.loadFactories(ModuleDetectionStrategy.class,
				Modules.class.getClassLoader());

		if (loadFactories.size() > 1) {

			throw new IllegalStateException(
					String.format("Multiple module detection strategies configured. Only one supported! %s",
							loadFactories));
		}

		DETECTION_STRATEGY = loadFactories.isEmpty() ? ModuleDetectionStrategies.DIRECT_SUB_PACKAGES : loadFactories.get(0);
	}

	private final ModulithMetadata metadata;
	private final Map<String, Module> modules;
	private final JavaClasses allClasses;
	private final List<JavaPackage> rootPackages;
	private final @With(AccessLevel.PRIVATE) @Getter Set<Module> sharedModules;

	private boolean verified;

	private Modules(ModulithMetadata metadata, Collection<String> packages, DescribedPredicate<JavaClass> ignored,
			boolean useFullyQualifiedModuleNames) {

		this.metadata = metadata;
		this.allClasses = new ClassFileImporter() //
				.withImportOption(new ImportOption.DoNotIncludeTests()) //
				.importPackages(packages) //
				.that(not(ignored));

		Classes classes = Classes.of(allClasses);

		this.modules = packages.stream() //
				.map(it -> JavaPackage.of(classes, it))
				.flatMap(DETECTION_STRATEGY::getModuleBasePackages) //
				.map(it -> new Module(it, useFullyQualifiedModuleNames)) //
				.collect(toMap(Module::getName, Function.identity()));

		this.rootPackages = packages.stream() //
				.map(it -> JavaPackage.of(classes, it).toSingle()) //
				.collect(Collectors.toList());

		this.sharedModules = Collections.emptySet();
	}

	/**
	 * Creates a new {@link Modules} relative to the given modulith type. Will inspect the {@link Modulith} annotation on
	 * the class given for advanced customizations of the module setup.
	 *
	 * @param modulithType must not be {@literal null}.
	 * @return
	 */
	public static Modules of(Class<?> modulithType) {
		return of(modulithType, alwaysFalse());
	}

	/**
	 * Creates a new {@link Modules} relative to the given modulith type, a {@link ModuleDetectionStrategy} and a
	 * {@link DescribedPredicate} which types and packages to ignore. Will inspect the {@link Modulith} and
	 * {@link Modulithic} annotations on the class given for advanced customizations of the module setup.
	 *
	 * @param modulithType must not be {@literal null}.
	 * @param detection must not be {@literal null}.
	 * @param ignored must not be {@literal null}.
	 * @return
	 */
	public static Modules of(Class<?> modulithType, DescribedPredicate<JavaClass> ignored) {

		CacheKey key = TypeKey.of(modulithType, ignored);

		return CACHE.computeIfAbsent(key, it -> {

			Assert.notNull(modulithType, "Modulith root type must not be null!");
			Assert.notNull(ignored, "Predicate to describe ignored types must not be null!");

			return of(key);
		});
	}

	/**
	 * Creates a new {@link Modules} instance for the given package name.
	 *
	 * @param javaPackage must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	public static Modules of(String javaPackage) {
		return of(javaPackage, alwaysFalse());
	}

	/**
	 * Creates a new {@link Modules} instance for the given package name and ignored classes.
	 *
	 * @param javaPackage must not be {@literal null} or empty.
	 * @param ignored must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	public static Modules of(String javaPackage, DescribedPredicate<JavaClass> ignored) {

		CacheKey key = PackageKey.of(javaPackage, ignored);

		return CACHE.computeIfAbsent(key, it -> {

			Assert.hasText(javaPackage, "Base package must not be null or empty!");
			Assert.notNull(ignored, "Predicate to describe ignored types must not be null!");

			return of(key);
		});
	}

	/**
	 * Creates a new {@link Modules} instance for the given {@link CacheKey}.
	 *
	 * @param key must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private static Modules of(CacheKey key) {

		Assert.notNull(key, "Cache key must not be null!");

		ModulithMetadata metadata = key.getMetadata();

		Set<String> basePackages = new HashSet<>();
		basePackages.add(key.getBasePackage());
		basePackages.addAll(metadata.getAdditionalPackages());

		Modules modules = new Modules(metadata, basePackages, key.getIgnored(),
				metadata.useFullyQualifiedModuleNames());

		Set<Module> sharedModules = metadata.getSharedModuleNames() //
				.map(modules::getRequiredModule) //
				.collect(Collectors.toSet());

		return modules.withSharedModules(sharedModules);
	}

	public Object getModulithSource() {
		return metadata.getModulithSource();
	}

	/**
	 * @return
	 * @deprecated since 1.1, as a {@link Modules} instance doesn't have to be created from a class in the first place.
	 *             For generic use, use {@link #getModulithSource()} instead.
	 */
	@Deprecated
	public Class<?> getModulithType() {

		Object source = getModulithSource();

		if (!Class.class.isInstance(source)) {
			throw new IllegalStateException(String.format("Moduliths not created from a type but %s!", source));
		}

		return (Class<?>) source;
	}

	/**
	 * Returns whether the given {@link JavaClass} is contained within the {@link Modules}.
	 *
	 * @param type must not be {@literal null}.
	 * @return
	 */
	public boolean contains(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return modules.values().stream() //
				.anyMatch(module -> module.contains(type));
	}

	/**
	 * Returns whether the given type is contained in one of the root packages (not including sub-packages) of the
	 * modules.
	 *
	 * @param className must not be {@literal null} or empty.
	 * @return
	 */
	public boolean withinRootPackages(String className) {

		Assert.hasText(className, "Class name must not be null or empty!");

		return rootPackages.stream().anyMatch(it -> it.contains(className));
	}

	/**
	 * Returns the {@link Module} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return
	 */
	public Optional<Module> getModuleByName(String name) {

		Assert.hasText(name, "Module name must not be null or empty!");

		return Optional.ofNullable(modules.get(name));
	}

	/**
	 * Returns the module that contains the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 * @return
	 */
	public Optional<Module> getModuleByType(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return modules.values().stream() //
				.filter(it -> it.contains(type)) //
				.findFirst();
	}

	/**
	 * Returns the {@link Module} containing the type with the given simple or fully-qualified name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	public Optional<Module> getModuleByType(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or empty!");

		return modules.values().stream() //
				.filter(it -> it.contains(candidate)) //
				.findFirst();
	}

	public Optional<Module> getModuleForPackage(String name) {

		return modules.values().stream() //
				.filter(it -> name.startsWith(it.getBasePackage().getName())) //
				.findFirst();
	}

	public void verify() {

		if (verified) {
			return;
		}

		Violations violations = detectViolations();

		this.verified = true;

		violations.throwIfPresent();
	}

	public Violations detectViolations() {

		Violations violations = rootPackages.stream() //
				.map(this::assertNoCyclesFor) //
				.flatMap(it -> it.getDetails().stream()) //
				.map(IllegalStateException::new) //
				.collect(Violations.toViolations());

		if (JMoleculesTypes.areRulesPresent()) {

			EvaluationResult result = JMoleculesDddRules.all().evaluate(allClasses);

			for (String message : result.getFailureReport().getDetails()) {
				violations = violations.and(message);
			}
		}

		return modules.values().stream() //
				.map(it -> it.detectDependencies(this)) //
				.reduce(violations, Violations::and);
	}

	private FailureReport assertNoCyclesFor(JavaPackage rootPackage) {

		EvaluationResult result = SlicesRuleDefinition.slices() //
				.matching(rootPackage.getName().concat(".(*)..")) //
				.should().beFreeOfCycles() //
				.evaluate(allClasses.that(resideInAPackage(rootPackage.getName().concat(".."))));

		return result.getFailureReport();
	}

	/**
	 * Returns all {@link Module}s.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<Module> stream() {
		return modules.values().stream();
	}

	/**
	 * Returns the system name if defined.
	 *
	 * @return
	 */
	public Optional<String> getSystemName() {
		return metadata.getSystemName();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Module> iterator() {
		return modules.values().iterator();
	}

	/**
	 * Returns the module with the given name rejecting invalid module names.
	 *
	 * @param moduleName must not be {@literal null}.
	 * @return
	 */
	private Module getRequiredModule(String moduleName) {

		Module module = modules.get(moduleName);

		if (module == null) {
			throw new IllegalArgumentException(String.format("Module %s does not exist!", moduleName));
		}

		return module;
	}

	public static class Filters {

		public static DescribedPredicate<JavaClass> withoutModules(String... names) {

			return Arrays.stream(names) //
					.map(it -> withoutModule(it)) //
					.reduce(DescribedPredicate.alwaysFalse(), DescribedPredicate::or, (__, right) -> right);
		}

		public static DescribedPredicate<JavaClass> withoutModule(String name) {
			return resideInAPackage("..".concat(name).concat(".."));
		}
	}

	private static interface CacheKey {

		String getBasePackage();

		DescribedPredicate<JavaClass> getIgnored();

		ModulithMetadata getMetadata();
	}

	@Value(staticConstructor = "of")
	private static final class TypeKey implements CacheKey {

		Class<?> type;
		DescribedPredicate<JavaClass> ignored;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Modules.CacheKey#getBasePackage()
		 */
		@Override
		public String getBasePackage() {
			return type.getPackage().getName();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Modules.CacheKey#getMetadata()
		 */
		@Override
		public ModulithMetadata getMetadata() {
			return ModulithMetadata.of(type);
		}
	}

	@Value(staticConstructor = "of")
	private static final class PackageKey implements CacheKey {

		String basePackage;
		DescribedPredicate<JavaClass> ignored;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Modules.CacheKey#getMetadata()
		 */
		@Override
		public ModulithMetadata getMetadata() {
			return ModulithMetadata.of(basePackage);
		}
	}
}
