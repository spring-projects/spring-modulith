/*
 * Copyright 2018-2024 the original author or authors.
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.springframework.aot.generate.Generated;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.modulith.core.Violations.Violation;
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
	private static final boolean JGRAPHT_PRESENT = ClassUtils.isPresent("org.jgrapht.Graph",
			ApplicationModules.class.getClassLoader());
	private static final DescribedPredicate<CanBeAnnotated> IS_AOT_TYPE;
	private static final DescribedPredicate<HasName> IS_SPRING_CGLIB_PROXY = nameContaining("$$SpringCGLIB$$");

	static {
		IS_AOT_TYPE = ClassUtils.isPresent("org.springframework.aot.generate.Generated",
				ApplicationModules.class.getClassLoader()) ? getAtGenerated() : DescribedPredicate.alwaysFalse();
	}

	@Nullable
	private static DescribedPredicate<CanBeAnnotated> getAtGenerated() {
		return annotatedWith(Generated.class);
	}

	private final ModulithMetadata metadata;
	private final Map<String, ApplicationModule> modules;
	private final JavaClasses allClasses;
	private final List<JavaPackage> rootPackages;
	private final Supplier<List<ApplicationModule>> rootModules;
	private final Set<ApplicationModule> sharedModules;
	private final List<String> orderedNames;

	private boolean verified;

	/**
	 * Creates a new {@link ApplicationModules} instance.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param ignored must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames can be {@literal null}.
	 * @param option must not be {@literal null}.
	 */
	protected ApplicationModules(ModulithMetadata metadata,
			DescribedPredicate<JavaClass> ignored, boolean useFullyQualifiedModuleNames, ImportOption option) {
		this(metadata, metadata.getBasePackages(), ignored, useFullyQualifiedModuleNames, option);
	}

	/**
	 * Creates a new {@link ApplicationModules} instance.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param packages must not be {@literal null}.
	 * @param ignored must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames can be {@literal null}.
	 * @param option must not be {@literal null}.
	 * @deprecated since 1.2, for removal in 1.3. Use {@link ApplicationModules(ModulithMetadata, DescribedPredicate,
	 *             boolean, ImportOption)} instead and set up {@link ModulithMetadata} to contain the packages you want to
	 *             use.
	 */
	@Deprecated(forRemoval = true)
	protected ApplicationModules(ModulithMetadata metadata, Collection<String> packages,
			DescribedPredicate<JavaClass> ignored, boolean useFullyQualifiedModuleNames, ImportOption option) {

		Assert.notNull(metadata, "ModulithMetadata must not be null!");
		Assert.notNull(packages, "Base packages must not be null!");
		Assert.notNull(ignored, "Ignores must not be null!");
		Assert.notNull(option, "ImportOptions must not be null!");

		this.metadata = metadata;
		this.allClasses = new ClassFileImporter() //
				.withImportOption(option) //
				.importPackages(packages) //
				.that(not(ignored.or(IS_AOT_TYPE).or(IS_SPRING_CGLIB_PROXY)));

		Assert.notEmpty(allClasses, () -> "No classes found in packages %s!".formatted(packages));

		Classes classes = Classes.of(allClasses);
		var strategy = ApplicationModuleDetectionStrategyLookup.getStrategy();

		this.modules = packages.stream() //
				.map(it -> JavaPackage.of(classes, it))
				.flatMap(strategy::getModuleBasePackages) //
				.map(it -> new ApplicationModule(it, useFullyQualifiedModuleNames)) //
				.collect(toMap(ApplicationModule::getName, Function.identity()));

		this.rootPackages = packages.stream() //
				.map(it -> JavaPackage.of(classes, it).toSingle()) //
				.toList();

		this.rootModules = SingletonSupplier.of(() -> rootPackages.stream()
				.map(ApplicationModules::rootModuleFor)
				.toList());

		this.sharedModules = Collections.emptySet();

		this.orderedNames = JGRAPHT_PRESENT //
				? TopologicalSorter.topologicallySortModules(this) //
				: modules.values().stream().map(ApplicationModule::getName).toList();
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
	 * @param orderedNames must not be {@literal null}.
	 * @param verified
	 */
	private ApplicationModules(ModulithMetadata metadata, Map<String, ApplicationModule> modules, JavaClasses allClasses,
			List<JavaPackage> rootPackages, Supplier<List<ApplicationModule>> rootModules,
			Set<ApplicationModule> sharedModules, List<String> orderedNames, boolean verified) {

		Assert.notNull(metadata, "ModulithMetadata must not be null!");
		Assert.notNull(modules, "Application modules must not be null!");
		Assert.notNull(allClasses, "JavaClasses must not be null!");
		Assert.notNull(rootPackages, "Root JavaPackages must not be null!");
		Assert.notNull(rootModules, "Root modules must not be null!");
		Assert.notNull(sharedModules, "Shared ApplicationModules must not be null!");
		Assert.notNull(orderedNames, "Ordered application module names must not be null!");

		this.metadata = metadata;
		this.modules = modules;
		this.allClasses = allClasses;
		this.rootPackages = rootPackages;
		this.rootModules = rootModules;
		this.sharedModules = sharedModules;
		this.orderedNames = orderedNames;
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
	public static ApplicationModules of(Class<?> modulithType, DescribedPredicate<JavaClass> ignored) {

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
	 * @deprecated use {@link #getSource()} instead
	 */
	@Deprecated(forRemoval = true)
	public Object getModulithSource() {
		return metadata.getSource();
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

		return modules.values().stream() //
				.anyMatch(module -> module.contains(type));
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
	 * @return
	 */
	public boolean withinRootPackages(String className) {

		Assert.hasText(className, "Class name must not be null or empty!");

		return rootPackages.stream().anyMatch(it -> it.contains(className));
	}

	/**
	 * Returns the {@link ApplicationModule} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return
	 */
	public Optional<ApplicationModule> getModuleByName(String name) {

		Assert.hasText(name, "Module name must not be null or empty!");

		return Optional.ofNullable(modules.get(name));
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
		return getModuleByType(candidate.getName());
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

		if (verified) {
			return this;
		}

		Violations violations = detectViolations();

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

		Violations violations = rootPackages.stream() //
				.map(this::assertNoCyclesFor) //
				.flatMap(it -> it.getDetails().stream()) //
				.map(Violation::new) //
				.collect(Violations.toViolations());

		if (JMoleculesTypes.areRulesPresent()) {

			EvaluationResult result = JMoleculesDddRules.all().evaluate(allClasses);

			for (String message : result.getFailureReport().getDetails()) {
				violations = violations.and(message);
			}
		}

		return Stream.concat(rootModules.get().stream(), modules.values().stream()) //
				.map(it -> it.detectDependencies(this)) //
				.reduce(violations, Violations::and);
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

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ApplicationModule> iterator() {
		return orderedNames.stream().map(this::getRequiredModule).iterator();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return this.stream()
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
				.evaluate(allClasses.that(resideInAPackage(rootPackage.getName().concat(".."))));

		return result.getFailureReport();
	}

	/**
	 * Returns the index of the module that contains the type of the given object or 1 if the given object is
	 * {@literal null} or its type does not reside in any module.
	 *
	 * @param object can be {@literal null}.
	 * @return
	 */
	private Integer getModuleIndexFor(@Nullable Object object) {

		return Optional.ofNullable(object)
				.map(it -> Class.class.isInstance(it) ? Class.class.cast(it) : it.getClass())
				.map(Class::getName)
				.flatMap(this::getModuleByType)
				.map(ApplicationModule::getName)
				.map(orderedNames::indexOf)
				.orElse(null);
	}

	/**
	 * Returns the module with the given name rejecting invalid module names.
	 *
	 * @param moduleName must not be {@literal null}.
	 * @return
	 */
	private ApplicationModule getRequiredModule(String moduleName) {

		var module = modules.get(moduleName);

		if (module == null) {
			throw new IllegalArgumentException(String.format("Module %s does not exist!", moduleName));
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
		return Stream.concat(modules.values().stream(), rootModules.get().stream());
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
			var modules = new ApplicationModules(metadata, key.getIgnored(),
					metadata.useFullyQualifiedModuleNames(), key.getOptions());

			var sharedModules = metadata.getSharedModuleNames() //
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
	private static ApplicationModule rootModuleFor(JavaPackage javaPackage) {

		return new ApplicationModule(javaPackage, true) {

			@Override
			public String getName() {
				return "root:" + super.getName();
			}

			@Override
			public boolean isRootModule() {
				return true;
			}
		};
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

		private final DescribedPredicate<JavaClass> ignored;
		private final ImportOption options;
		private final Object metadataSource;
		private final Supplier<ModulithMetadata> metadata;

		public CacheKey(DescribedPredicate<JavaClass> ignored, ImportOption options, Object metadataSource,
				Supplier<ModulithMetadata> metadata) {

			this.ignored = ignored;
			this.options = options;
			this.metadataSource = metadataSource;
			this.metadata = SingletonSupplier.of(metadata);
		}

		static CacheKey of(String pkg, DescribedPredicate<JavaClass> ignored, ImportOption options) {
			return new CacheKey(ignored, options, pkg, () -> ModulithMetadata.of(pkg));
		}

		static CacheKey of(Class<?> type, DescribedPredicate<JavaClass> ignored, ImportOption options) {
			return new CacheKey(ignored, options, type, () -> ModulithMetadata.of(type));
		}

		DescribedPredicate<JavaClass> getIgnored() {
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
		public boolean equals(Object obj) {

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

	/**
	 * Dedicated class to be able to only optionally depend on the JGraphT library.
	 *
	 * @author Oliver Drotbohm
	 */
	private static class TopologicalSorter {

		private static List<String> topologicallySortModules(ApplicationModules modules) {

			Graph<ApplicationModule, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

			modules.modules.forEach((__, project) -> {

				graph.addVertex(project);

				project.getDependencies(modules).stream() //
						.map(ApplicationModuleDependency::getTargetModule) //
						.forEach(dependency -> {
							graph.addVertex(dependency);
							graph.addEdge(project, dependency);
						});
			});

			var names = new ArrayList<String>();
			var iterator = new TopologicalOrderIterator<>(graph);

			try {

				iterator.forEachRemaining(it -> names.add(0, it.getName()));
				return names;

			} catch (IllegalArgumentException o_O) {
				return modules.modules.values().stream().map(ApplicationModule::getName).toList();
			}
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
					.map(ApplicationModule::getName)
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
