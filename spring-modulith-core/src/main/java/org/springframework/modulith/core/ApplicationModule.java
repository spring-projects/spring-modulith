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

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static java.lang.System.*;
import static org.springframework.modulith.core.Types.*;
import static org.springframework.modulith.core.Types.JavaXTypes.*;
import static org.springframework.modulith.core.Types.SpringDataTypes.*;
import static org.springframework.modulith.core.Types.SpringTypes.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.modulith.core.Types.SpringTypes;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.SourceCodeLocation;

/**
 * An application module.
 *
 * @author Oliver Drotbohm
 */
public class ApplicationModule {

	/**
	 * The base package of the {@link ApplicationModule}.
	 */
	private final JavaPackage basePackage;
	private final ApplicationModuleInformation information;

	/**
	 * All {@link NamedInterfaces} of the {@link ApplicationModule} either declared explicitly via {@link NamedInterface}
	 * or implicitly.
	 */
	private final NamedInterfaces namedInterfaces;
	private final boolean useFullyQualifiedModuleNames;

	private final Supplier<Classes> springBeans;
	private final Supplier<Classes> aggregateRoots;
	private final Supplier<List<JavaClass>> valueTypes;
	private final Supplier<List<EventType>> publishedEvents;

	/**
	 * Creates a new {@link ApplicationModule} for the given base package and whether to use fully-qualified module names.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames
	 */
	ApplicationModule(JavaPackage basePackage, boolean useFullyQualifiedModuleNames) {

		this.basePackage = basePackage;
		this.information = ApplicationModuleInformation.of(basePackage);
		this.namedInterfaces = NamedInterfaces.discoverNamedInterfaces(basePackage);
		this.useFullyQualifiedModuleNames = useFullyQualifiedModuleNames;

		this.springBeans = SingletonSupplier.of(() -> filterSpringBeans(basePackage));
		this.aggregateRoots = SingletonSupplier.of(() -> findAggregateRoots(basePackage));
		this.valueTypes = SingletonSupplier
				.of(() -> findArchitecturallyEvidentType(ArchitecturallyEvidentType::isValueObject));
		this.publishedEvents = SingletonSupplier.of(() -> findPublishedEvents());
	}

	/**
	 * Returns the module's base package.
	 *
	 * @return the basePackage
	 */
	public JavaPackage getBasePackage() {
		return basePackage;
	}

	/**
	 * Returns all {@link NamedInterfaces} exposed by the module.
	 *
	 * @return the namedInterfaces will never be {@literal null}.
	 */
	public NamedInterfaces getNamedInterfaces() {
		return namedInterfaces;
	}

	/**
	 * Returns the logical name of the module.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	public String getName() {
		return useFullyQualifiedModuleNames ? basePackage.getName() : basePackage.getLocalName();
	}

	/**
	 * Returns the name of the {@link ApplicationModule} for display purposes.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	public String getDisplayName() {
		return information.getDisplayName()
				.orElseGet(() -> StringUtils.capitalize(basePackage.getLocalName()));
	}

	/**
	 * Returns {@link DeclaredDependencies} of the current {@link ApplicationModule}.
	 *
	 * @param modules must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public ApplicationModuleDependencies getDependencies(ApplicationModules modules, DependencyType... type) {

		Assert.notNull(modules, "ApplicationModules must not be null!");
		Assert.notNull(type, "DependencyTypes must not be null!");

		var dependencies = getAllModuleDependencies(modules) //
				.filter(it -> type.length == 0 ? true : Arrays.stream(type).anyMatch(it::hasType)) //
				.distinct() //
				.<ApplicationModuleDependency> flatMap(it -> DefaultApplicationModuleDependency.of(it, modules)) //
				.toList();

		return ApplicationModuleDependencies.of(dependencies, modules);
	}

	/**
	 * Returns all event types the current module exposes an event listener for.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 */
	public List<JavaClass> getEventsListenedTo(ApplicationModules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		return getAllModuleDependencies(modules) //
				.filter(it -> it.type == DependencyType.EVENT_LISTENER) //
				.map(QualifiedDependency::getTarget) //
				.toList();
	}

	/**
	 * Returns all {@link EventType}s published by the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<EventType> getPublishedEvents() {
		return publishedEvents.get();
	}

	/**
	 * Returns all value types contained in the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<JavaClass> getValueTypes() {
		return valueTypes.get();
	}

	/**
	 * Returns all types that are considered aggregate roots.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<JavaClass> getAggregateRoots() {

		return aggregateRoots.get().stream() //
				.flatMap(this::resolveModuleSuperTypes) //
				.distinct() //
				.toList();
	}

	/**
	 * Returns all modules that contain types which the types of the current module depend on.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 */
	public Stream<ApplicationModule> getBootstrapDependencies(ApplicationModules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		return getBootstrapDependencies(modules, DependencyDepth.IMMEDIATE);
	}

	public Stream<ApplicationModule> getBootstrapDependencies(ApplicationModules modules, DependencyDepth depth) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.notNull(depth, "Dependency depth must not be null!");

		return streamBootstrapDependencies(modules, depth);
	}

	/**
	 * Returns all {@link JavaPackage} for the current module including the ones by its dependencies.
	 *
	 * @param modules must not be {@literal null}.
	 * @param depth must not be {@literal null}.
	 * @return
	 */
	public Stream<JavaPackage> getBootstrapBasePackages(ApplicationModules modules, DependencyDepth depth) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.notNull(depth, "Dependency depth must not be null!");

		var dependencies = streamBootstrapDependencies(modules, depth);

		return Stream.concat(Stream.of(this), dependencies) //
				.map(ApplicationModule::getBasePackage);
	}

	/**
	 * Returns all {@link SpringBean}s contained in the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<SpringBean> getSpringBeans() {

		return getSpringBeansInternal().stream() //
				.map(it -> SpringBean.of(it, this)) //
				.toList();
	}

	/**
	 * Returns the {@link ArchitecturallyEvidentType} for the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @throws IllegalArgumentException if the given type is not a module type.
	 */
	public ArchitecturallyEvidentType getArchitecturallyEvidentType(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return getType(type.getName())
				.map(it -> ArchitecturallyEvidentType.of(it, getSpringBeansInternal()))
				.orElseThrow(() -> new IllegalArgumentException("Couldn't find type %s in module %s!".formatted(
						FormatableType.of(type).getAbbreviatedFullName(this), getName())));
	}

	public boolean contains(JavaClass type) {
		return basePackage.contains(type);
	}

	public boolean contains(@Nullable Class<?> type) {
		return type != null && getType(type.getName()).isPresent();
	}

	/**
	 * Returns the {@link JavaClass} for the given candidate simple of fully-qualified type name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Optional<JavaClass> getType(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or emtpy!");

		return basePackage.stream()
				.filter(hasSimpleOrFullyQualifiedName(candidate))
				.findFirst();
	}

	/**
	 * Returns whether the given {@link JavaClass} is exposed by the current module, i.e. whether it's part of any of the
	 * module's named interfaces.
	 *
	 * @param type must not be {@literal null}.
	 * @return
	 */
	public boolean isExposed(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return namedInterfaces.stream().anyMatch(it -> it.contains(type));
	}

	public void verifyDependencies(ApplicationModules modules) {
		detectDependencies(modules).throwIfPresent();
	}

	public Violations detectDependencies(ApplicationModules modules) {

		return getAllModuleDependencies(modules) //
				.map(it -> it.isValidDependencyWithin(modules)) //
				.reduce(Violations.NONE, Violations::and);
	}

	/**
	 * Returns whether the module is considered a root one, i.e., it is an artificial one created for each base package
	 * configured.
	 * 
	 * @return whether the module is considered a root one.
	 * @since 1.1
	 */
	public boolean isRootModule() {
		return false;
	}

	/**
	 * Returns whether the module has a base package with the given name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return whether the module has a base package with the given name.
	 * @since 1.1
	 */
	boolean hasBasePackage(String candidate) {
		return basePackage.getName().equals(candidate);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return toString(null);
	}

	public String toString(@Nullable ApplicationModules modules) {

		var builder = new StringBuilder("# ").append(getDisplayName()).append("\n");

		builder.append("> Logical name: ").append(getName()).append('\n');
		builder.append("> Base package: ").append(basePackage.getName()).append('\n');

		if (namedInterfaces.hasExplicitInterfaces()) {

			builder.append("> Named interfaces:\n");

			namedInterfaces.forEach(it -> builder.append("  + ") //
					.append(it.toString()) //
					.append('\n'));
		}

		if (modules != null) {

			List<ApplicationModule> dependencies = getBootstrapDependencies(modules).toList();

			builder.append("> Direct module dependencies: ");
			builder.append(dependencies.isEmpty() ? "none"
					: dependencies.stream().map(ApplicationModule::getName).collect(Collectors.joining(", ")));
			builder.append('\n');
		}

		Classes beans = getSpringBeansInternal();

		if (beans.isEmpty()) {

			builder.append("> Spring beans: none\n");

		} else {

			builder.append("> Spring beans:\n");
			beans.forEach(it -> builder.append("  ") //
					.append(Classes.format(it, basePackage.getName(), isExposed(it)))//
					.append('\n'));
		}

		return builder.toString();
	}

	Classes getSpringBeansInternal() {
		return springBeans.get();
	}

	/**
	 * Returns all declared module dependencies, either explicitly declared or defined as shared on the given
	 * {@link ApplicationModules} instance.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 */
	DeclaredDependencies getDeclaredDependencies(ApplicationModules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		var allowedDependencyNames = information.getDeclaredDependencies();

		if (DeclaredDependencies.isOpen(allowedDependencyNames)) {
			return DeclaredDependencies.open();
		}

		var explicitlyDeclaredModules = allowedDependencyNames.stream() //
				.map(it -> DeclaredDependency.of(it, this, modules));

		var sharedDependencies = modules.getSharedModules().stream()
				.map(DeclaredDependency::to);

		return Stream.concat(explicitlyDeclaredModules, sharedDependencies) //
				.distinct() //
				.collect(Collectors.collectingAndThen(Collectors.toList(), DeclaredDependencies::closed));
	}

	/**
	 * Returns whether the given module contains a type with the given simple or fully qualified name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return
	 */
	boolean contains(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or empty!");

		return getType(candidate).isPresent();
	}

	/**
	 * Returns whether the {@link ApplicationModule} contains the package with the given name, which means the given
	 * package is either the module's base package or a sub package of it.
	 *
	 * @param packageName must not be {@literal null} or empty.
	 * @return whether the {@link ApplicationModule} contains the package with the given name.
	 * @since 1.0.2
	 */
	boolean containsPackage(String packageName) {

		Assert.hasText(packageName, "Package name must not be null or empty!");

		var basePackageName = basePackage.getName();

		return packageName.equals(basePackageName)
				|| packageName.startsWith(basePackageName + ".");
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ApplicationModule that)) {
			return false;
		}

		return Objects.equals(this.basePackage, that.basePackage) //
				&& Objects.equals(this.aggregateRoots, that.aggregateRoots) //
				&& Objects.equals(this.information, that.information) //
				&& Objects.equals(this.namedInterfaces, that.namedInterfaces) //
				&& Objects.equals(this.publishedEvents, that.publishedEvents) //
				&& Objects.equals(this.springBeans, that.springBeans) //
				&& Objects.equals(this.useFullyQualifiedModuleNames, that.useFullyQualifiedModuleNames) //
				&& Objects.equals(this.valueTypes, that.valueTypes);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(basePackage, aggregateRoots, information, namedInterfaces, publishedEvents, springBeans,
				useFullyQualifiedModuleNames, valueTypes);
	}

	private List<EventType> findPublishedEvents() {

		DescribedPredicate<JavaClass> isEvent = implement(JMoleculesTypes.DOMAIN_EVENT) //
				.or(isAnnotatedWith(JMoleculesTypes.AT_DOMAIN_EVENT));

		return basePackage.that(isEvent).stream() //
				.map(EventType::new).toList();
	}

	/**
	 * Returns a {@link Stream} of all super types of the given one that are declared in the same module as well as the
	 * type itself.
	 *
	 * @param type must not be {@literal null}.
	 * @return
	 */
	private Stream<JavaClass> resolveModuleSuperTypes(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return Stream.concat(//
				type.getAllRawSuperclasses().stream().filter(this::contains), //
				Stream.of(type));
	}

	private Stream<QualifiedDependency> getAllModuleDependencies(ApplicationModules modules) {

		return basePackage.stream() //
				.flatMap(it -> getModuleDependenciesOf(it, modules));
	}

	private Stream<ApplicationModule> streamBootstrapDependencies(ApplicationModules modules, DependencyDepth depth) {

		return switch (depth) {
			case NONE -> Stream.empty();
			case IMMEDIATE -> getDirectModuleBootstrapDependencies(modules);
			case ALL -> getAllBootstrapDependencies(modules);
			default -> getAllBootstrapDependencies(modules);
		};
	}

	private Stream<ApplicationModule> getAllBootstrapDependencies(ApplicationModules modules) {

		return getDirectModuleBootstrapDependencies(modules) //
				.flatMap(it -> Stream.concat(Stream.of(it), it.streamBootstrapDependencies(modules, DependencyDepth.ALL))) //
				.distinct();
	}

	private Stream<ApplicationModule> getDirectModuleBootstrapDependencies(ApplicationModules modules) {

		var beans = getSpringBeansInternal();

		return beans.stream() //
				.map(it -> ArchitecturallyEvidentType.of(it, beans)) //
				.flatMap(it -> QualifiedDependency.fromType(it)) //
				.filter(it -> isDependencyToOtherModule(it.target, modules)) //
				.filter(it -> it.hasType(DependencyType.USES_COMPONENT)) //
				.map(it -> modules.getModuleByType(it.target)) //
				.distinct() //
				.flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty));
	}

	private Stream<QualifiedDependency> getModuleDependenciesOf(JavaClass type, ApplicationModules modules) {

		var evidentType = ArchitecturallyEvidentType.of(type, getSpringBeansInternal());

		var injections = QualifiedDependency.fromType(evidentType) //
				.filter(it -> isDependencyToOtherModule(it.getTarget(), modules)); //

		var directDependencies = type.getDirectDependenciesFromSelf().stream() //
				.filter(it -> isDependencyToOtherModule(it.getTargetClass(), modules)) //
				.map(QualifiedDependency::new);

		return Stream.concat(injections, directDependencies).distinct();
	}

	private boolean isDependencyToOtherModule(JavaClass dependency, ApplicationModules modules) {
		return modules.contains(dependency) && !contains(dependency);
	}

	private Classes findAggregateRoots(JavaPackage source) {

		return source.stream() //
				.map(it -> ArchitecturallyEvidentType.of(it, getSpringBeansInternal()))
				.filter(ArchitecturallyEvidentType::isAggregateRoot) //
				.map(ArchitecturallyEvidentType::getType) //
				.collect(Classes.toClasses());
	}

	private static Classes filterSpringBeans(JavaPackage source) {

		Map<Boolean, List<JavaClass>> collect = source.that(isConfiguration()).stream() //
				.flatMap(it -> it.getMethods().stream()) //
				.filter(SpringTypes::isAtBeanMethod) //
				.map(JavaMethod::getRawReturnType) //
				.collect(Collectors.groupingBy(it -> source.contains(it)));

		Classes repositories = source.that(isSpringDataRepository());
		Classes coreComponents = source.that(not(INTERFACES).and(isComponent()));
		Classes configurationProperties = source.that(isConfigurationProperties());
		Classes jsr303Validator = source.that(isJsr303Validator());

		return coreComponents //
				.and(repositories) //
				.and(configurationProperties) //
				.and(jsr303Validator) //
				.and(collect.getOrDefault(true, Collections.emptyList())) //
				.and(collect.getOrDefault(false, Collections.emptyList()));
	}

	private static Predicate<JavaClass> hasSimpleOrFullyQualifiedName(String candidate) {
		return it -> it.getSimpleName().equals(candidate) || it.getFullName().equals(candidate);
	}

	private List<JavaClass> findArchitecturallyEvidentType(Predicate<ArchitecturallyEvidentType> selector) {

		var springBeansInternal = getSpringBeansInternal();

		return basePackage.stream()
				.map(it -> ArchitecturallyEvidentType.of(it, springBeansInternal))
				.filter(selector)
				.map(ArchitecturallyEvidentType::getType)
				.toList();
	}

	static class DeclaredDependency {

		private static final String INVALID_EXPLICIT_MODULE_DEPENDENCY = "Invalid explicit module dependency in %s! No module found with name '%s'.";
		private static final String INVALID_NAMED_INTERFACE_DECLARATION = "No named interface named '%s' found! Original dependency declaration: %s -> %s.";

		private final ApplicationModule target;
		private final NamedInterface namedInterface;

		/**
		 * Creates a new {@link DeclaredDependency} for the given {@link ApplicationModule} and {@link NamedInterface}.
		 *
		 * @param target must not be {@literal null}.
		 * @param namedInterface must not be {@literal null}.
		 */
		private DeclaredDependency(ApplicationModule target, NamedInterface namedInterface) {

			Assert.notNull(target, "Target ApplicationModule must not be null!");
			Assert.notNull(namedInterface, "NamedInterface must not be null!");

			this.target = target;
			this.namedInterface = namedInterface;
		}

		/**
		 * Creates an {@link DeclaredDependency} to the module and optionally named interface defined by the given
		 * identifier.
		 *
		 * @param identifier must not be {@literal null} or empty. Follows the
		 *          {@code ${moduleName}(::${namedInterfaceName})} pattern.
		 * @param source the source module of the dependency, must not be {@literal null}.
		 * @param modules must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @throws IllegalArgumentException in case the given identifier is invalid, i.e. does not refer to an existing
		 *           module or named interface.
		 */
		public static DeclaredDependency of(String identifier, ApplicationModule source,
				ApplicationModules modules) {

			Assert.hasText(identifier, "Module dependency identifier must not be null or empty!");

			var segments = identifier.split("::");
			var targetModuleName = segments[0].trim();
			var namedInterfacename = segments.length > 1 ? segments[1].trim() : null;

			var target = modules.getModuleByName(targetModuleName)
					.orElseThrow(() -> new IllegalArgumentException(
							INVALID_EXPLICIT_MODULE_DEPENDENCY.formatted(source.getName(), targetModuleName)));

			var namedInterface = namedInterfacename == null
					? target.getNamedInterfaces().getUnnamedInterface()
					: target.getNamedInterfaces().getByName(segments[1])
							.orElseThrow(() -> new IllegalArgumentException(
									INVALID_NAMED_INTERFACE_DECLARATION.formatted(namedInterfacename, source.getName(), identifier)));

			return new DeclaredDependency(target, namedInterface);
		}

		/**
		 * Creates a new {@link DeclaredDependency} to the unnamed interface of the given {@link ApplicationModule}.
		 *
		 * @param module must not be {@literal null}.
		 * @return
		 */
		public static DeclaredDependency to(ApplicationModule module) {

			Assert.notNull(module, "ApplicationModule must not be null!");

			return new DeclaredDependency(module, module.getNamedInterfaces().getUnnamedInterface());
		}

		/**
		 * Returns whether the {@link DeclaredDependency} contains the given {@link JavaClass}.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		public boolean contains(JavaClass type) {

			Assert.notNull(type, "Type must not be null!");

			return namedInterface.contains(type);
		}

		/**
		 * Returns whether the {@link DeclaredDependency} contains the given {@link Class}.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		public boolean contains(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");

			return namedInterface.contains(type);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return namedInterface.isUnnamed() ? target.getName() : target.getName() + "::" + namedInterface.getName();
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof DeclaredDependency that)) {
				return false;
			}

			return Objects.equals(this.target, that.target) //
					&& Objects.equals(this.namedInterface, that.namedInterface);

		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(target, namedInterface);
		}
	}

	/**
	 * A collection wrapper for {@link DeclaredDependency} instances.
	 *
	 * @author Oliver Drotbohm
	 */
	static class DeclaredDependencies {

		private static final String OPEN_TOKEN = "¯\\_(ツ)_/¯";

		private final List<DeclaredDependency> dependencies;
		private final boolean closed;

		static boolean isOpen(List<String> declaredDependencies) {
			return declaredDependencies.size() == 1 && declaredDependencies.get(0).equals(OPEN_TOKEN);
		}

		public static DeclaredDependencies open() {
			return new DeclaredDependencies(Collections.emptyList(), false);
		}

		public static DeclaredDependencies closed(List<DeclaredDependency> dependencies) {
			return new DeclaredDependencies(dependencies, true);
		}

		/**
		 * Creates a new {@link DeclaredDependencies} for the given {@link List} of {@link DeclaredDependency}.
		 *
		 * @param dependencies must not be {@literal null}.
		 */
		private DeclaredDependencies(List<DeclaredDependency> dependencies, boolean closed) {

			Assert.notNull(dependencies, "Dependencies must not be null!");

			this.dependencies = dependencies;
			this.closed = closed;
		}

		/**
		 * Returns whether the given {@link JavaClass} is a valid dependency.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		public boolean isAllowedDependency(JavaClass type) {
			return isAllowedDependency(it -> it.contains(type));
		}

		public boolean isAllowedDependency(Class<?> type) {
			return isAllowedDependency(it -> it.contains(type));
		}

		private boolean isAllowedDependency(Predicate<DeclaredDependency> predicate) {

			Assert.notNull(predicate, "Predicate must not be null!");

			return closed ? !dependencies.isEmpty() && contains(predicate) : dependencies.isEmpty() || contains(predicate);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {

			return dependencies.isEmpty() //
					? "none" //
					: dependencies.stream().map(DeclaredDependency::toString).collect(Collectors.joining(", "));
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof DeclaredDependencies that)) {
				return false;
			}

			return Objects.equals(this.dependencies, that.dependencies);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(dependencies);
		}

		/**
		 * Returns whether any of the dependencies contains the given {@link JavaClass}.
		 *
		 * @param type must not be {@literal null}.
		 */
		private boolean contains(Predicate<DeclaredDependency> condition) {

			Assert.notNull(condition, "Condition must not be null!");

			return dependencies.stream().anyMatch(condition);
		}
	}

	static class QualifiedDependency {

		private static final List<String> INJECTION_TYPES = Arrays.asList(AT_AUTOWIRED, AT_RESOURCE, AT_INJECT);

		private final JavaClass source, target;
		private final String description;
		private final DependencyType type;

		/**
		 * Creates a new {@link QualifiedDependency} from the given source and target {@link JavaClass}, description and
		 * {@link DependencyType}.
		 *
		 * @param source must not be {@literal null}.
		 * @param target must not be {@literal null}.
		 * @param description must not be {@literal null}.
		 * @param type must not be {@literal null}.
		 */
		public QualifiedDependency(JavaClass source, JavaClass target, String description, DependencyType type) {

			Assert.notNull(source, "Source JavaClass must not be null!");
			Assert.notNull(target, "Target JavaClass must not be null!");
			Assert.notNull(description, "Description must not be null!");
			Assert.notNull(type, "DependencyType must not be null!");

			this.source = source;
			this.target = target;
			this.description = description;
			this.type = type;
		}

		QualifiedDependency(Dependency dependency) {
			this(dependency.getOriginClass(), //
					dependency.getTargetClass(), //
					dependency.getDescription(), //
					DependencyType.forDependency(dependency));
		}

		static QualifiedDependency fromCodeUnitParameter(JavaCodeUnit codeUnit, JavaClass parameter) {

			var description = createDescription(codeUnit, parameter, "parameter");
			var type = DependencyType.forCodeUnit(codeUnit) //
					.defaultOr(() -> DependencyType.forParameter(parameter));

			return new QualifiedDependency(codeUnit.getOwner(), parameter, description, type);
		}

		static QualifiedDependency fromCodeUnitReturnType(JavaCodeUnit codeUnit) {

			var description = createDescription(codeUnit, codeUnit.getRawReturnType(), "return type");

			return new QualifiedDependency(codeUnit.getOwner(), codeUnit.getRawReturnType(), description,
					DependencyType.DEFAULT);
		}

		static Stream<QualifiedDependency> fromType(ArchitecturallyEvidentType type) {

			var source = type.getType();

			return Stream.concat(Stream.concat(fromConstructorOf(type), fromMethodsOf(source)), fromFieldsOf(source));
		}

		static Stream<QualifiedDependency> allFrom(JavaCodeUnit codeUnit) {

			var parameterDependencies = codeUnit.getRawParameterTypes()//
					.stream() //
					.map(it -> fromCodeUnitParameter(codeUnit, it));

			var returnType = Stream.of(fromCodeUnitReturnType(codeUnit));

			return Stream.concat(parameterDependencies, returnType);
		}

		/**
		 * Returns the source {@link JavaClass}.
		 *
		 * @return the source will never be {@literal null}.
		 */
		public JavaClass getSource() {
			return source;
		}

		/**
		 * Returns the target {@link JavaClass}.
		 *
		 * @return the target must not be {@literal null}.
		 */
		public JavaClass getTarget() {
			return target;
		}

		/**
		 * Returns whether the {@link QualifiedDependency} has the given {@link DependencyType}.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		boolean hasType(DependencyType type) {
			return this.type.equals(type);
		}

		Violations isValidDependencyWithin(ApplicationModules modules) {

			var originModule = getExistingModuleOf(source, modules);
			var targetModule = getExistingModuleOf(target, modules);

			DeclaredDependencies declaredDependencies = originModule.getDeclaredDependencies(modules);
			Violations violations = Violations.NONE;

			// Check explicitly defined allowed targets
			if (!declaredDependencies.isAllowedDependency(target)) {

				var message = "Module '%s' depends on module '%s' via %s -> %s. Allowed targets: %s." //
						.formatted(originModule.getName(), targetModule.getName(), source.getName(), target.getName(),
								declaredDependencies.toString());

				return violations.and(new IllegalStateException(message));
			}

			// No explicitly allowed dependencies - check for general access

			if (!targetModule.isExposed(target)) {

				var violationText = "Module '%s' depends on non-exposed type %s within module '%s'!"
						.formatted(originModule.getName(), target.getName(), targetModule.getName());

				return violations.and(new IllegalStateException(violationText + lineSeparator() + description));
			}

			return violations;
		}

		ApplicationModule getExistingModuleOf(JavaClass javaClass, ApplicationModules modules) {

			return modules.getModuleByType(javaClass).orElseThrow(() -> new IllegalStateException(
					String.format("Source/Target of a %s should always be within a module, but %s is not",
							getClass().getSimpleName(), javaClass.getName())));
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return type.format(FormatableType.of(source), FormatableType.of(target));
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof QualifiedDependency other)) {
				return false;
			}

			return Objects.equals(this.source, other.source) //
					&& Objects.equals(this.target, other.target) //
					&& Objects.equals(this.description, other.description) //
					&& Objects.equals(this.type, other.type); //
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(source, target, description, type);
		}

		private static Stream<QualifiedDependency> fromConstructorOf(ArchitecturallyEvidentType source) {

			var type = source.getType();
			var constructors = type.getConstructors();

			return constructors.stream() //
					.filter(it -> constructors.size() == 1 || isInjectionPoint(it)) //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.map(parameter -> {
								return source.isInjectable() && !source.isConfigurationProperties()
										? new InjectionDependency(it, parameter)
										: new QualifiedDependency(type, parameter, createDescription(it, parameter, "parameter"),
												DependencyType.DEFAULT);
							}));
		}

		private static Stream<QualifiedDependency> fromFieldsOf(JavaClass source) {

			return source.getAllFields().stream() //
					.filter(QualifiedDependency::isInjectionPoint) //
					.map(field -> new InjectionDependency(field, field.getRawType()));
		}

		private static Stream<QualifiedDependency> fromMethodsOf(JavaClass source) {

			var methods = source.getAllMethods().stream() //
					.filter(it -> !it.getOwner().isEquivalentTo(Object.class)) //
					.collect(Collectors.toSet());

			if (methods.isEmpty()) {
				return Stream.empty();
			}

			var returnTypes = methods.stream() //
					.filter(it -> !it.getRawReturnType().isPrimitive()) //
					.filter(it -> !it.getRawReturnType().getPackageName().startsWith("java")) //
					.map(it -> fromCodeUnitReturnType(it));

			var injectionMethods = methods.stream() //
					.filter(QualifiedDependency::isInjectionPoint) //
					.collect(Collectors.toSet());

			var methodInjections = injectionMethods.stream() //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.map(parameter -> new InjectionDependency(it, parameter)));

			var otherMethods = methods.stream() //
					.filter(it -> !injectionMethods.contains(it)) //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.map(parameter -> fromCodeUnitParameter(it, parameter)));

			return Stream.concat(Stream.concat(methodInjections, otherMethods), returnTypes);
		}

		private static String createDescription(JavaMember codeUnit, JavaClass declaringElement,
				String declarationDescription) {

			var type = declaringElement.getSimpleName();

			var codeUnitDescription = JavaConstructor.class.isInstance(codeUnit) //
					? String.format("%s", declaringElement.getSimpleName()) //
					: String.format("%s.%s", declaringElement.getSimpleName(), codeUnit.getName());

			if (JavaCodeUnit.class.isInstance(codeUnit)) {
				codeUnitDescription = String.format("%s(%s)", codeUnitDescription,
						JavaCodeUnit.class.cast(codeUnit).getRawParameterTypes().stream() //
								.map(JavaClass::getSimpleName) //
								.collect(Collectors.joining(", ")));
			}

			var annotations = codeUnit.getAnnotations().stream() //
					.filter(it -> INJECTION_TYPES.contains(it.getRawType().getName())) //
					.map(it -> "@" + it.getRawType().getSimpleName()) //
					.collect(Collectors.joining(" ", "", " "));

			annotations = StringUtils.hasText(annotations) ? annotations : "";

			var declaration = declarationDescription + " " + annotations + codeUnitDescription;
			var location = SourceCodeLocation.of(codeUnit.getOwner(), 0).toString();

			return String.format("%s declares %s in %s", type, declaration, location);
		}

		private static boolean isInjectionPoint(JavaMember unit) {
			return INJECTION_TYPES.stream().anyMatch(type -> unit.isAnnotatedWith(type));
		}
	}

	private static class InjectionDependency extends QualifiedDependency {

		private final JavaMember source;
		private final boolean isConfigurationClass;

		/**
		 * Creates a new {@link InjectionDependency} for the given source, target and originating member.
		 *
		 * @param source must not be {@literal null}.
		 * @param target must not be {@literal null}.
		 */
		public InjectionDependency(JavaMember source, JavaClass target) {

			super(source.getOwner(), target,
					QualifiedDependency.createDescription(source, source.getOwner(), getDescriptionFor(source)),
					DependencyType.USES_COMPONENT);

			Assert.notNull(source, "Originating member must not be null!");

			this.source = source;
			this.isConfigurationClass = isConfiguration().test(source.getOwner());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.ModuleDependency#isValidDependencyWithin(org.springframework.modulith.model.Modules)
		 */
		@Override
		Violations isValidDependencyWithin(ApplicationModules modules) {

			Violations violations = super.isValidDependencyWithin(modules);

			if (JavaField.class.isInstance(source) && !isConfigurationClass) {

				ApplicationModule module = getExistingModuleOf(source.getOwner(), modules);

				violations = violations.and(new IllegalStateException(
						String.format("Module %s uses field injection in %s. Prefer constructor injection instead!",
								module.getDisplayName(), source.getFullName())));
			}

			return violations;
		}

		private static String getDescriptionFor(JavaMember member) {

			if (JavaConstructor.class.isInstance(member)) {
				return "constructor";
			} else if (JavaMethod.class.isInstance(member)) {
				return "injection method";
			} else if (JavaField.class.isInstance(member)) {
				return "injected field";
			}

			throw new IllegalArgumentException(String.format("Invalid member type %s!", member.toString()));
		}
	}

	private static class DefaultApplicationModuleDependency implements ApplicationModuleDependency {

		private final QualifiedDependency dependency;
		private final ApplicationModule target;

		/**
		 * Creates a new {@link ApplicationModuleDependency} for the given {@link QualifiedDependency} and
		 * {@link ApplicationModules}.
		 *
		 * @param dependency must not be {@literal null}.
		 * @param target must not be {@literal null}.
		 */
		private DefaultApplicationModuleDependency(QualifiedDependency dependency, ApplicationModule target) {

			Assert.notNull(dependency, "QualifiedDependency must not be null!");
			Assert.notNull(target, "Target ApplicationModule must not be null!");

			this.dependency = dependency;
			this.target = target;
		}

		/**
		 * Creates a new {@link Stream} of {@link ApplicationModuleDependency} for the given {@link QualifiedDependency} and
		 * {@link ApplicationModules}.
		 *
		 * @param dependency must not be {@literal null}.
		 * @param modules must not be {@literal null}.
		 */
		static Stream<ApplicationModuleDependency> of(QualifiedDependency dependency, ApplicationModules modules) {

			return modules.getModuleByType(dependency.getTarget()).stream()
					.map(it -> new DefaultApplicationModuleDependency(dependency, it));
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.MaterializedDependency#getSourceType()
		 */
		@Override
		public JavaClass getSourceType() {
			return dependency.source;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.MaterializedDependency#getTargetType()
		 */
		@Override
		public JavaClass getTargetType() {
			return dependency.target;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.MaterializedDependency#getDependencyType()
		 */
		@Override
		public DependencyType getDependencyType() {
			return dependency.type;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.MaterializedDependency#getTargetModule()
		 */
		@Override
		public ApplicationModule getTargetModule() {
			return target;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "DefaultApplicationModuleDependency [dependency=" + dependency + ", target=" + target + "]";
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof DefaultApplicationModuleDependency other)) {
				return false;
			}

			return Objects.equals(this.target, other.target) //
					&& Objects.equals(this.dependency, other.dependency);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(target, dependency);
		}
	}
}
