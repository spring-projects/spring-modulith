/*
 * Copyright 2018-2026 the original author or authors.
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

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.*;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.*;
import static java.util.stream.Collectors.*;
import static org.springframework.modulith.core.Violations.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.Generated;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.SingletonSupplier;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.FailureReport;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * @author Oliver Drotbohm
 * @author Peter Gafert
 */
public class ApplicationModules implements Iterable<ApplicationModule> {

	private static final Map<CacheKey, ApplicationModules> CACHE = new ConcurrentHashMap<>();

	private static final ImportOption IMPORT_OPTION = new ImportOption.DoNotIncludeTests();
	private static final @Nullable DescribedPredicate<CanBeAnnotated> IS_GENERATED;
	private static final DescribedPredicate<HasName> IS_SPRING_CGLIB_PROXY = nameContaining("$$SpringCGLIB$$");

	static {
		IS_GENERATED = ClassUtils.isPresent("org.springframework.aot.generate.Generated",
				ApplicationModules.class.getClassLoader()) ? getAtGenerated() : DescribedPredicate.alwaysFalse();
	}

	@Nullable
	private static DescribedPredicate<CanBeAnnotated> getAtGenerated() {
		return annotatedWith(Generated.class);
	}

	private final ModulithMetadata metadata;
	private final Map<ApplicationModuleIdentifier, ApplicationModule> modules;
	private final JavaClasses allClasses;
	private final List<JavaPackage> rootPackages;
	private final Supplier<List<ApplicationModule>> rootModules;
	private final Set<ApplicationModule> sharedModules;
	private final List<ApplicationModuleIdentifier> orderedNames;

	private boolean verified;

	/**
	 * Creates a new {@link ApplicationModules} instance.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param ignored must not be {@literal null}.
	 * @param option must not be {@literal null}.
	 */
	protected ApplicationModules(ModulithMetadata metadata,
			DescribedPredicate<? super JavaClass> ignored, ImportOption option) {
		this(metadata, metadata.getBasePackages(), ignored, metadata.useFullyQualifiedModuleNames(), option);
	}

	/**
	 * Creates a new {@link ApplicationModules} instance.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param packages must not be {@literal null}.
	 * @param ignored must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames can be {@literal null}.
	 * @param option must not be {@literal null}.
	 */
	private ApplicationModules(ModulithMetadata metadata, Collection<String> packages,
			DescribedPredicate<? super JavaClass> ignored, boolean useFullyQualifiedModuleNames, ImportOption option) {

		Assert.notNull(metadata, "ModulithMetadata must not be null!");
		Assert.notNull(packages, "Base packages must not be null!");
		Assert.notNull(ignored, "Ignores must not be null!");
		Assert.notNull(option, "ImportOptions must not be null!");

		DescribedPredicate<? super JavaClass> excluded = DescribedPredicate.or(ignored, IS_GENERATED,
				IS_SPRING_CGLIB_PROXY);

		this.metadata = metadata;
		var importer = new ClassFileImporter() //
				.withImportOption(option);
		var loader = SpringFactoriesLoader.forResourceLocation(ApplicationModuleSourceContributions.LOCATION,
				ApplicationModules.class.getClassLoader());
		var factories = loader.load(ApplicationModuleSourceFactory.class);
		var allPackages = Stream.concat(packages.stream(), factories.stream().flatMap(it -> it.getRootPackages().stream()))
				.distinct()
				.toList();

		this.allClasses = importer //
				.importPackages(allPackages) //
				.that(not(excluded));

		Assert.notEmpty(allClasses, () -> "No classes found in packages %s!".formatted(packages));

		Classes classes = Classes.of(allClasses);
		var strategy = ApplicationModuleDetectionStrategyLookup.getStrategy();

		var contributions = ApplicationModuleSourceContributions.of(factories,
				pkgs -> filterByPackages(allClasses, pkgs), strategy, useFullyQualifiedModuleNames);

		var directSources = packages.stream() //
				.distinct()
				.map(it -> JavaPackage.of(classes, it))
				.flatMap(it -> ApplicationModuleSource.from(it, strategy, useFullyQualifiedModuleNames));

		var sources = Stream.concat(directSources, contributions.getSources())
				.distinct()
				.collect(Collectors.toUnmodifiableSet());

		this.modules = sources.stream() //
				.map(it -> {

					var exclusions = sources.stream()
							.map(ApplicationModuleSource::getModuleBasePackage)
							.toList();

					return new ApplicationModule(it, new JavaPackages(exclusions));

				})
				.collect(toMap(ApplicationModule::getIdentifier, Function.identity()));

		this.rootPackages = Stream.concat(packages.stream(), contributions.getRootPackages()) //
				.distinct()
				.map(it -> JavaPackage.of(classes, it).toSingle()) //
				.toList();

		this.rootModules = SingletonSupplier.of(() -> rootPackages.stream()
				.map(ApplicationModules::rootModuleFor)
				.toList());

		this.sharedModules = Collections.emptySet();
		this.orderedNames = topologicallyOrderIdentifiers(this);
	}

	private static JavaClasses filterByPackages(JavaClasses source, Collection<String> packages) {

		Assert.notNull(source, "Source JavaClasses must not be null!");
		Assert.notNull(packages, "Packages must not be null!");

		if (packages.isEmpty()) {
			return source.that(alwaysFalse());
		}

		var filters = packages.stream()
				.map(it -> it + "..")
				.toArray(String[]::new);

		return source.that(resideInAnyPackage(filters));
	}

	/**
	 * Creates a new {@link ApplicationModules} for the given {@link ModulithMetadata}, {@link ApplicationModule}s,
	 * {@link JavaClasses}, {@link JavaPackage}s, root and shared {@link ApplicationModule}s, ordered module names and
	 * verified flag.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param modules must not be {@literal null}.
	 * @param allClasses must not be {@literal null}.
	 * @param rootPackages must not be {@literal null}.
	 * @param rootModules must not be {@literal null}.
	 * @param sharedModules must not be {@literal null}.
	 * @param orderedIdentifiers must not be {@literal null}.
	 * @param verified
	 */
	private ApplicationModules(ModulithMetadata metadata, Map<ApplicationModuleIdentifier, ApplicationModule> modules,
			JavaClasses allClasses, List<JavaPackage> rootPackages, Supplier<List<ApplicationModule>> rootModules,
			Set<ApplicationModule> sharedModules, List<ApplicationModuleIdentifier> orderedIdentifiers, boolean verified) {

		Assert.notNull(metadata, "ModulithMetadata must not be null!");
		Assert.notNull(modules, "Application modules must not be null!");
		Assert.notNull(allClasses, "JavaClasses must not be null!");
		Assert.notNull(rootPackages, "Root JavaPackages must not be null!");
		Assert.notNull(rootModules, "Root modules must not be null!");
		Assert.notNull(sharedModules, "Shared ApplicationModules must not be null!");
		Assert.notNull(orderedIdentifiers, "Ordered application module identifiers must not be null!");

		this.metadata = metadata;
		this.modules = modules;
		this.allClasses = allClasses;
		this.rootPackages = rootPackages;
		this.rootModules = rootModules;
		this.sharedModules = sharedModules;
		this.orderedNames = orderedIdentifiers;
		this.verified = verified;
	}

	/**
	 * Creates a new {@link ApplicationModules} relative to the given modulith type. Will inspect the
	 * {@link org.springframework.modulith.Modulith} annotation on the class given for advanced customizations of the
	 * module setup.
	 *
	 * @param modulithType must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModules of(Class<?> modulithType) {
		return of(modulithType, alwaysFalse());
	}

	/**
	 * Creates a new {@link ApplicationModules} relative to the given modulith type, and a {@link DescribedPredicate}
	 * which types and packages to ignore. Will inspect the {@link org.springframework.modulith.Modulith} and
	 * {@link org.springframework.modulith.Modulithic} annotations on the class given for advanced customizations of the
	 * module setup.
	 *
	 * @param modulithType must not be {@literal null}.
	 * @param ignored must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModules of(Class<?> modulithType, DescribedPredicate<? super JavaClass> ignored) {

		Assert.notNull(modulithType, "Modulith root type must not be null!");
		Assert.notNull(ignored, "Predicate to describe ignored types must not be null!");

		return of(CacheKey.of(modulithType, ignored, IMPORT_OPTION));
	}

	/**
	 * Creates a new {@link ApplicationModules} instance for the given type and {@link ImportOption}.
	 *
	 * @param modulithType must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.2
	 */
	public static ApplicationModules of(Class<?> modulithType, ImportOption options) {
		return of(CacheKey.of(modulithType, alwaysFalse(), options));
	}

	/**
	 * Creates a new {@link ApplicationModules} instance for the given package name.
	 *
	 * @param javaPackage must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModules of(String javaPackage) {
		return of(javaPackage, alwaysFalse());
	}

	/**
	 * Creates a new {@link ApplicationModules} instance for the given package name and ignored classes.
	 *
	 * @param javaPackage must not be {@literal null} or empty.
	 * @param ignored must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModules of(String javaPackage, DescribedPredicate<JavaClass> ignored) {

		Assert.hasText(javaPackage, "Base package must not be null or empty!");
		Assert.notNull(ignored, "Predicate to describe ignored types must not be null!");

		return of(CacheKey.of(javaPackage, ignored, IMPORT_OPTION));
	}

	/**
	 * Creates a new {@link ApplicationModules} instance for the given package and {@link ImportOption}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.2
	 */
	public static ApplicationModules of(String javaPackage, ImportOption options) {
		return of(CacheKey.of(javaPackage, alwaysFalse(), options));
	}

	/**
	 * Returns the source of the {@link ApplicationModules}. Either a main application class or a package name.
	 *
	 * @return will never be {@literal null}.
	 */
	public Object getSource() {
		return metadata.getSource();
	}

	/**
	 * Returns all root packages.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.4
	 */
	public List<JavaPackage> getRootPackages() {
		return Collections.unmodifiableList(rootPackages);
	}

	/**
	 * Returns all {@link ApplicationModule}s registered as shared ones.
	 *
	 * @return will never be {@literal null}.
	 */
	public Set<ApplicationModule> getSharedModules() {
		return sharedModules;
	}

	/**
	 * Returns whether the given {@link JavaClass} is contained within the {@link ApplicationModules}.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return modules.values().stream().anyMatch(module -> module.contains(type));
	}

	/**
	 * Returns whether the given {@link Class} is contained within the {@link ApplicationModules}.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return allClasses.contain(type) && contains(allClasses.get(type));
	}

	/**
	 * Returns whether the given type is contained in one of the root packages (not including sub-packages) of the
	 * modules.
	 *
	 * @param className must not be {@literal null} or empty.
	 */
	public boolean withinRootPackages(String className) {

		Assert.hasText(className, "Class name must not be null or empty!");

		var candidate = PackageName.ofType(className);

		return rootPackages.stream()
				.map(JavaPackage::getPackageName)
				.anyMatch(candidate::equals);
	}

	/**
	 * Returns the {@link ApplicationModule} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return
	 */
	public Optional<ApplicationModule> getModuleByName(String name) {

		Assert.hasText(name, "Module name must not be null or empty!");

		return Optional.ofNullable(modules.get(ApplicationModuleIdentifier.of(name)));
	}

	/**
	 * Returns the module that contains the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 * @return
	 */
	public Optional<ApplicationModule> getModuleByType(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return allModules() //
				.filter(it -> it.contains(type)) //
				.findFirst();
	}

	/**
	 * Returns the {@link ApplicationModule} containing the type with the given simple or fully-qualified name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Optional<ApplicationModule> getModuleByType(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or empty!");

		return allModules() //
				.filter(it -> it.contains(candidate)) //
				.findFirst();
	}

	/**
	 * Returns the {@link ApplicationModule} containing the given type.
	 *
	 * @param candidate must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public Optional<ApplicationModule> getModuleByType(Class<?> candidate) {

		return allModules()
				.filter(it -> it.contains(candidate))
				.findFirst();
	}

	/**
	 * Returns the {@link ApplicationModule} containing the given package.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Optional<ApplicationModule> getModuleForPackage(String name) {

		return modules.values().stream() //
				.filter(it -> it.containsPackage(name)) //
				.findFirst()
				.or(() -> {
					return rootModules.get().stream()
							.filter(it -> it.hasBasePackage(name))
							.findFirst();
				});
	}

	/**
	 * Execute all verifications to be applied, unless the verification has been executed before.
	 *
	 * @return will never be {@literal null}.
	 */
	public ApplicationModules verify() {
		return verify(VerificationOptions.defaults());
	}

	/**
	 * Execute all verifications to be applied considering the given {@link VerificationOptions}, unless the verification
	 * has been executed before.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.4
	 */
	public ApplicationModules verify(VerificationOptions options) {

		if (verified) {
			return this;
		}

		Violations violations = detectViolations(options);

		this.verified = true;

		violations.throwIfPresent();

		return this;
	}

	/**
	 * Executes all verifications to be applied and returns {@link Violations} if any occured. Will always execute the
	 * verifications in contrast to {@link #verify()} which just runs once.
	 *
	 * @return will never be {@literal null}.
	 * @see Violations#throwIfPresent()
	 */
	public Violations detectViolations() {
		return detectViolations(VerificationOptions.defaults());
	}

	/**
	 * Executes all verifications to be applied considering the given {@link VerificationOptions} and returns
	 * {@link Violations} if any occured. Will always execute the verifications in contrast to {@link #verify()} which
	 * just runs once.
	 *
	 * @return will never be {@literal null}.
	 * @see Violations#throwIfPresent()
	 * @since 1.4
	 */
	public Violations detectViolations(VerificationOptions options) {

		var cycleViolations = rootPackages.stream() //
				.map(this::assertNoCyclesFor) //
				.flatMap(it -> it.getDetails().stream()) //
				.collect(toViolations());

		var additionalViolations = options.getAdditionalVerifications().stream()
				.map(it -> it.evaluate(allClasses))
				.map(EvaluationResult::getFailureReport)
				.flatMap(it -> it.getDetails().stream())
				.collect(toViolations());

		var dependencyViolations = allModules() //
				.map(it -> it.detectDependencies(this)) //
				.reduce(NONE, Violations::and);

		return cycleViolations.and(additionalViolations).and(dependencyViolations);
	}

	/**
	 * Returns all {@link ApplicationModule}s.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModule> stream() {
		return StreamSupport.stream(this.spliterator(), false);
	}

	/**
	 * Returns the system name if defined.
	 *
	 * @return
	 */
	public Optional<String> getSystemName() {
		return metadata.getSystemName();
	}

	/**
	 * Returns a {@link Comparator} that will sort objects based on their types' application module. In other words,
	 * objects of types in more fundamental modules will be ordered before ones residing in downstream modules. For
	 * example, if module A depends on B, objects of types residing in B will be ordered before ones in A. For objects
	 * residing in the same module, standard Spring-based ordering (via {@link org.springframework.core.annotation.Order}
	 * or {@link org.springframework.core.Ordered}) will be applied.
	 *
	 * @return will never be {@literal null}.
	 */
	public Comparator<Object> getComparator() {

		return (left, right) -> {

			var leftIndex = getModuleIndexFor(left);

			if (leftIndex == null) {
				return 1;
			}

			var rightIndex = getModuleIndexFor(right);

			if (rightIndex == null) {
				return -1;
			}

			var result = leftIndex - rightIndex;

			return result != 0 ? result : AnnotationAwareOrderComparator.INSTANCE.compare(left, right);
		};
	}

	/**
	 * Returns the parent {@link ApplicationModule} if the given one has one.
	 *
	 * @param module must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	public Optional<ApplicationModule> getParentOf(ApplicationModule module) {

		Assert.notNull(module, "ApplicationModule must not be null!");

		return module.getParentModule(this);
	}

	/**
	 * Returns whether the given {@link ApplicationModule} has a parent one.
	 *
	 * @param module must not be {@literal null}.
	 * @since 1.3
	 */
	public boolean hasParent(ApplicationModule module) {
		return getParentOf(module).isPresent();
	}

	/**
	 * Returns all nested modules of the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.4.2
	 */
	public Collection<ApplicationModule> getNestedModules(ApplicationModule module) {
		return module.getNestedModules(this);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ApplicationModule> iterator() {
		return orderedModules().iterator();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return this.stream()
				.sorted()
				.map(it -> it.toString(this))
				.collect(Collectors.joining("\n"));
	}

	private ApplicationModules withSharedModules(Set<ApplicationModule> sharedModules) {
		return new ApplicationModules(metadata, modules, allClasses, rootPackages, rootModules, sharedModules, orderedNames,
				verified);
	}

	private FailureReport assertNoCyclesFor(JavaPackage rootPackage) {

		var result = SlicesRuleDefinition.slices() //
				.assignedFrom(new ApplicationModulesSliceAssignment())
				.should().beFreeOfCycles() //
				.evaluate(allClasses.that(resideInAPackage(rootPackage.asFilter())));

		return result.getFailureReport();
	}

	/**
	 * Returns the index of the module that contains the type of the given object or 1 if the given object is
	 * {@literal null} or its type does not reside in any module.
	 *
	 * @param object can be {@literal null}.
	 * @return can be {@literal null}.
	 */
	private @Nullable Integer getModuleIndexFor(@Nullable Object object) {

		return Optional.ofNullable(object)
				.map(it -> Class.class.isInstance(it) ? Class.class.cast(it) : it.getClass())
				.map(Class::getName)
				.flatMap(this::getModuleByType)
				.map(ApplicationModule::getIdentifier)
				.map(orderedNames::indexOf)
				.orElse(null);
	}

	/**
	 * Returns the module with the given name rejecting invalid module names.
	 *
	 * @param moduleName must not be {@literal null}.
	 * @return
	 */
	private ApplicationModule getRequiredModule(ApplicationModuleIdentifier identifier) {

		var module = modules.get(identifier);

		if (module == null) {
			throw new IllegalArgumentException(String.format("Module %s does not exist!", identifier));
		}

		return module;
	}

	/**
	 * Returns of all {@link ApplicationModule}s, including root ones (last).
	 *
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	private Stream<ApplicationModule> allModules() {
		return Stream.concat(orderedModules(), rootModules.get().stream());
	}

	private Stream<ApplicationModule> orderedModules() {
		return orderedNames != null
				? orderedNames.stream().map(this::getRequiredModule)
				: modules.values().stream().sorted();
	}

	/**
	 * Creates a new {@link ApplicationModules} instance for the given {@link CacheKey}.
	 *
	 * @param key must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private static ApplicationModules of(CacheKey cacheKey) {

		Assert.notNull(cacheKey, "Cache key must not be null!");

		return CACHE.computeIfAbsent(cacheKey, key -> {

			var metadata = key.getMetadata();
			var modules = new ApplicationModules(metadata, key.getIgnored(), key.getOptions());

			var sharedModules = metadata.getSharedModuleIdentifiers() //
					.map(modules::getRequiredModule) //
					.collect(Collectors.toSet());

			return modules.withSharedModules(sharedModules);
		});
	}

	/**
	 * Creates a special root {@link ApplicationModule} for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	private static ApplicationModule rootModuleFor(JavaPackage pkg) {

		var source = ApplicationModuleSource.from(pkg, "root:" + pkg.getName());

		return new ApplicationModule(source, JavaPackages.NONE) {

			@Override
			public boolean isRootModule() {
				return true;
			}
		};
	}

	/**
	 * Returns all {@link ApplicationModuleIdentifier} topologically sorted.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private static final List<ApplicationModuleIdentifier> topologicallyOrderIdentifiers(ApplicationModules modules) {

		Supplier<List<ApplicationModuleIdentifier>> fallback = () -> modules.stream()
				.map(ApplicationModule::getIdentifier)
				.sorted()
				.toList();

		return Optional.ofNullable(ModuleSorter.sortTopologically(modules)).orElseGet(fallback);
	}

	public static class Filters {

		public static DescribedPredicate<JavaClass> withoutModules(String... names) {

			return Arrays.stream(names) //
					.map(it -> withoutModule(it)) //
					.reduce(DescribedPredicate.alwaysFalse(), (left, right) -> left.or(right), (__, right) -> right);
		}

		public static DescribedPredicate<JavaClass> withoutModule(String name) {
			return resideInAPackage("..".concat(name).concat(".."));
		}
	}

	private static class CacheKey {

		private final DescribedPredicate<? super JavaClass> ignored;
		private final ImportOption options;
		private final Object metadataSource;
		private final Supplier<ModulithMetadata> metadata;

		public CacheKey(DescribedPredicate<? super JavaClass> ignored, ImportOption options, Object metadataSource,
				Supplier<ModulithMetadata> metadata) {

			this.ignored = ignored;
			this.options = options;
			this.metadataSource = metadataSource;
			this.metadata = SingletonSupplier.of(metadata);
		}

		static CacheKey of(String pkg, DescribedPredicate<? super JavaClass> ignored, ImportOption options) {
			return new CacheKey(ignored, options, pkg, () -> ModulithMetadata.of(pkg));
		}

		static CacheKey of(Class<?> type, DescribedPredicate<? super JavaClass> ignored, ImportOption options) {
			return new CacheKey(ignored, options, type, () -> ModulithMetadata.of(type));
		}

		DescribedPredicate<? super JavaClass> getIgnored() {
			return ignored;
		}

		ModulithMetadata getMetadata() {
			return metadata.get();
		}

		ImportOption getOptions() {
			return options;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(@Nullable Object obj) {

			if (obj == this) {
				return true;
			}

			if (!(obj instanceof CacheKey that)) {
				return false;
			}

			return Objects.equals(this.ignored, that.ignored)
					&& Objects.equals(this.options, that.options)
					&& Objects.equals(this.metadataSource, that.metadataSource);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(ignored, options, metadataSource);
		}
	}

	private static class ModuleSorter {

		/**
		 * Returns a topologically sorted list of {@link ApplicationModuleIdentifier} for the given
		 * {@link ApplicationModules} or {@literal null} in case we detect a dependency cycle.
		 *
		 * @param modules must not be {@literal null}.
		 * @return can be {@literal null}.
		 */
		public static @Nullable List<ApplicationModuleIdentifier> sortTopologically(ApplicationModules modules) {

			var visited = new HashSet<ApplicationModule>();
			var inProgress = new HashSet<ApplicationModule>();
			var levelMap = new TreeMap<Integer, Set<ApplicationModuleIdentifier>>();

			boolean cycleDetected = modules.stream()
					.filter(module -> !visited.contains(module))
					.anyMatch(module -> visit(module, modules, visited, inProgress, 0, levelMap));

			if (cycleDetected) {
				return null;
			}

			return levelMap.values().stream().flatMap(Set::stream).toList();
		}

		private static boolean visit(ApplicationModule module, ApplicationModules modules, Set<ApplicationModule> visited,
				Set<ApplicationModule> inProgress, int level, Map<Integer, Set<ApplicationModuleIdentifier>> levelMap) {

			if (module.isRootModule()) {
				return false;
			}

			if (inProgress.contains(module)) {
				return true;
			}

			if (visited.contains(module)) {
				return false;
			}

			inProgress.add(module);

			var cycleDetected = module.getDirectDependencies(modules).uniqueModules()
					.anyMatch(dependency -> visit(dependency, modules, visited, inProgress, level + 1, levelMap));

			if (cycleDetected) {
				return true;
			}

			inProgress.remove(module);
			visited.add(module);

			int maxDependencyLevel = module.getDirectDependencies(modules).uniqueModules()
					.mapToInt(it -> findLevelForClosestDependency(it, levelMap))
					.max()
					.orElse(-1);

			levelMap.computeIfAbsent(maxDependencyLevel + 1, __ -> new TreeSet<>()).add(module.getIdentifier());

			return false;
		}

		private static int findLevelForClosestDependency(ApplicationModule module,
				Map<Integer, Set<ApplicationModuleIdentifier>> levelMap) {

			return levelMap.entrySet().stream()
					.filter(it -> it.getValue().contains(module.getIdentifier()))
					.mapToInt(Entry::getKey)
					.findFirst()
					.orElse(-1);
		}
	}

	private class ApplicationModulesSliceAssignment implements SliceAssignment {

		/*
		 * (non-Javadoc)
		 * @see com.tngtech.archunit.library.dependencies.SliceAssignment#getIdentifierOf(com.tngtech.archunit.core.domain.JavaClass)
		 */
		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {

			return getModuleByType(javaClass)
					.filter(Predicate.not(ApplicationModule::isOpen))
					.map(ApplicationModule::getIdentifier)
					.map(ApplicationModuleIdentifier::toString)
					.map(SliceIdentifier::of)
					.orElse(SliceIdentifier.ignore());
		}

		/*
		 * (non-Javadoc)
		 * @see com.tngtech.archunit.base.HasDescription#getDescription()
		 */
		@Override
		public String getDescription() {
			return "Appliction module slices " + ApplicationModules.this.modules.keySet();
		}
	}
}
