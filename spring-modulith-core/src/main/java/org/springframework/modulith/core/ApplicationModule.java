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
import static java.lang.System.*;
import static java.util.Comparator.*;
import static org.springframework.modulith.core.SyntacticSugar.*;
import static org.springframework.modulith.core.Types.JavaXTypes.*;
import static org.springframework.modulith.core.Types.SpringDataTypes.*;
import static org.springframework.modulith.core.Types.SpringTypes.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.modulith.core.Types.JavaTypes;
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
public class ApplicationModule implements Comparable<ApplicationModule> {

	/**
	 * The base package of the {@link ApplicationModule}.
	 */
	private final JavaPackage basePackage;
	private final Classes classes;
	private final JavaPackages exclusions;

	private final ApplicationModuleInformation information;

	/**
	 * All {@link NamedInterfaces} of the {@link ApplicationModule} either declared explicitly via {@link NamedInterface}
	 * or implicitly.
	 */
	private final NamedInterfaces namedInterfaces;
	private final ApplicationModuleSource source;

	private final Supplier<Classes> springBeans;
	private final Supplier<Classes> aggregateRoots;
	private final Supplier<List<JavaClass>> valueTypes;
	private final Supplier<List<EventType>> publishedEvents;

	/**
	 * Creates a new {@link ApplicationModule} from the given {@link ApplicationModuleSource}.
	 *
	 * @param source must not be {@literal null}.
	 */
	ApplicationModule(ApplicationModuleSource source) {
		this(source, JavaPackages.NONE);
	}

	/**
	 * Creates a new {@link ApplicationModule} for the given base package and whether to use fully-qualified module names.
	 *
	 * @param source must not be {@literal null}.
	 * @param exclusions must not be {@literal null}.
	 */
	ApplicationModule(ApplicationModuleSource source, JavaPackages exclusions) {

		Assert.notNull(source, "Base package must not be null!");
		Assert.notNull(exclusions, "Exclusions must not be null!");

		JavaPackage basePackage = source.getModuleBasePackage().without(exclusions);

		this.source = source;
		this.basePackage = basePackage;
		this.exclusions = exclusions.getSubPackagesOf(basePackage);
		this.classes = basePackage.getClasses();
		this.information = ApplicationModuleInformation.of(basePackage);
		this.namedInterfaces = source.getNamedInterfaces(information, basePackage);

		this.springBeans = SingletonSupplier.of(() -> filterSpringBeans(classes));
		this.aggregateRoots = SingletonSupplier.of(() -> findAggregateRoots(classes));
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
	 * Returns the logical identifier of the module.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	public ApplicationModuleIdentifier getIdentifier() {
		return source.getIdentifier();
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
	 * Returns the direct {@link ApplicationModuleDependencies} of the current {@link ApplicationModule}.
	 *
	 * @param modules must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public ApplicationModuleDependencies getDirectDependencies(ApplicationModules modules, DependencyType... type) {
		return getDependencies(modules, DependencyDepth.IMMEDIATE, type);
	}

	/**
	 * Returns the all {@link ApplicationModuleDependencies} (including transitive ones) of the current
	 * {@link ApplicationModule}.
	 *
	 * @param modules must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public ApplicationModuleDependencies getAllDependencies(ApplicationModules modules, DependencyType... type) {
		return getDependencies(modules, DependencyDepth.ALL, type);
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
	 * Returns all {@link SpringBean}s assignable to the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.4
	 */
	public List<SpringBean> getSpringBeans(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return getSpringBeansInternal().stream() //
				.map(it -> SpringBean.of(it, this)) //
				.filter(it -> it.isAssignableTo(type))
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
						FormattableType.of(type).getAbbreviatedFullName(this), getIdentifier())));
	}

	/**
	 * Returns whether the current module contains the given type.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(JavaClass type) {
		return classes.contains(type);
	}

	/**
	 * Returns whether the current module contains the given type.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(Class<?> type) {
		return classes.contains(type);
	}

	/**
	 * Returns whether the module could contain the type independent of the actual types backing the current instance. The
	 * mismatch usually comes from the distinction between production and test code. A module set up from the former would
	 * not contain a test type within its package space. This method in contrast would acknowledge that the test type
	 * logically belongs to the module, too.
	 *
	 * @param type must not be {@literal null}.
	 * @see JavaPackage#couldContain(Class)
	 * @since 1.4.6, 2.0.1
	 */
	public boolean couldContain(Class<?> type) {

		Assert.notNull(type, "Class<?> must not be null!");

		return contains(type) || !exclusions.couldContain(type) && basePackage.couldContain(type);
	}

	/**
	 * Returns the {@link JavaClass} for the given candidate simple or fully-qualified type name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Optional<JavaClass> getType(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or emtpy!");

		return classes.stream()
				.filter(hasSimpleOrFullyQualifiedName(candidate))
				.findFirst();
	}

	/**
	 * Returns whether the given {@link JavaClass} is exposed by the current module, i.e. whether it's part of any of the
	 * module's named interfaces.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean isExposed(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return namedInterfaces.stream().anyMatch(it -> it.contains(type));
	}

	/**
	 * Returns whether the given {@link JavaClass} is exposed by the current module, i.e. whether it's part of any of the
	 * module's named interfaces.
	 *
	 * @param type must not be {@literal null}.
	 * @since 1.2.8, 1.3.2
	 */
	public boolean isExposed(Class<?> type) {

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
	 * Returns whether the given module contains a type with the given simple or fully qualified name.
	 *
	 * @param candidate must not be {@literal null}.
	 * @since 1.3
	 */
	public boolean contains(String candidate) {

		var candidatePackageName = PackageName.ofType(candidate);

		return (PackageName.isDefault(candidatePackageName) || basePackage.getPackageName().contains(candidatePackageName))
				&& getType(candidate).isPresent();
	}

	public String toString(@Nullable ApplicationModules modules) {

		var builder = new StringBuilder("# ").append(getDisplayName());

		if (isOpen()) {
			builder.append(" (open)");
		}

		builder.append("\n");

		if (modules != null) {
			modules.getParentOf(this).ifPresent(it -> {
				builder.append("> Parent module: ").append(it.getIdentifier()).append("\n");
			});
		}

		builder.append("> Logical name: ").append(getIdentifier()).append('\n');
		builder.append("> Base package: ").append(basePackage.getName()).append('\n');

		builder.append("> Excluded packages: ");

		if (!exclusions.iterator().hasNext()) {
			builder.append("none").append('\n');
		} else {

			builder.append('\n');

			exclusions.stream().forEach(it -> {
				builder.append("  - ").append(it.getName()).append('\n');
			});
		}

		if (namedInterfaces.hasExplicitInterfaces()) {

			builder.append("> Named interfaces:\n");

			namedInterfaces.forEach(it -> builder.append("  + ") //
					.append(it.toString()) //
					.append('\n'));
		}

		if (modules != null) {

			var directDependencies = getDependencies(modules, DependencyDepth.IMMEDIATE);
			builder.append("> Direct module dependencies: ");
			builder.append(directDependencies.isEmpty() ? "none"
					: toBulletList(directDependencies.uniqueModules(), getBootstrapDependencies(modules)));
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

	Classes getSpringBeansInternal() {
		return springBeans.get();
	}

	/**
	 * Returns all declared allowed module dependencies, either explicitly declared or defined as shared on the given
	 * {@link ApplicationModules} instance.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.4.2, previously package private
	 */
	public AllowedDependencies getAllowedDependencies(ApplicationModules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		var allowedDependencyNames = information.getDeclaredDependencies();

		if (AllowedDependencies.isOpen(allowedDependencyNames)) {
			return AllowedDependencies.open();
		}

		var explicitlyDeclaredModules = allowedDependencyNames.stream() //
				.map(it -> AllowedDependency.of(it, this, modules));

		var sharedDependencies = modules.getSharedModules().stream()
				.map(AllowedDependency::to);

		return Stream.concat(explicitlyDeclaredModules, sharedDependencies) //
				.distinct() //
				.collect(Collectors.collectingAndThen(Collectors.toList(), AllowedDependencies::closed));
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

	/**
	 * Returns whether the module is considered open.
	 *
	 * @see org.springframework.modulith.ApplicationModule.Type
	 * @since 1.4.2, previously package private since 1.2.
	 */
	public boolean isOpen() {
		return information.isOpen();
	}

	/**
	 * Returns whether the given type is contained in any of the parent modules of the current one.
	 *
	 * @param type must not be {@literal null}.
	 * @param modules must not be {@literal null}.
	 * @since 1.3
	 */
	boolean containsTypeInAnyParent(JavaClass type, ApplicationModules modules) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(modules, "ApplicationModules must not be null!");

		return modules.getParentOf(this)
				.filter(it -> it.contains(type) || it.containsTypeInAnyParent(type, modules))
				.isPresent();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ApplicationModuleDependenciesAware#getDependencies(org.springframework.modulith.core.ApplicationModules, org.springframework.modulith.core.DependencyDepth, org.springframework.modulith.core.DependencyType[])
	 */
	public ApplicationModuleDependencies getDependencies(ApplicationModules modules, DependencyDepth depth,
			DependencyType... types) {

		Assert.notNull(modules, "ApplicationModules must not be null!");
		Assert.notNull(depth, "DependencyDepth must not be null!");
		Assert.notNull(types, "DependencyTypes must not be null!");

		if (depth == DependencyDepth.NONE) {
			return ApplicationModuleDependencies.NONE;
		}

		return getDependencies(modules, new DependencyTraversal(depth, types))
				.collect(Collectors.collectingAndThen(Collectors.toList(), ApplicationModuleDependencies::of));
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

		return Objects.equals(this.source, that.source)
				&& Objects.equals(this.basePackage, that.basePackage) //
				&& Objects.equals(this.aggregateRoots, that.aggregateRoots) //
				&& Objects.equals(this.information, that.information) //
				&& Objects.equals(this.namedInterfaces, that.namedInterfaces) //
				&& Objects.equals(this.publishedEvents, that.publishedEvents) //
				&& Objects.equals(this.springBeans, that.springBeans) //
				&& Objects.equals(this.valueTypes, that.valueTypes);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(source, basePackage, aggregateRoots, information, namedInterfaces, publishedEvents, springBeans,
				valueTypes);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return toString(null);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(ApplicationModule o) {
		return getBasePackage().compareTo(o.getBasePackage());
	}

	private List<EventType> findPublishedEvents() {

		DescribedPredicate<JavaClass> isEvent = implement(JMoleculesTypes.DOMAIN_EVENT) //
				.or(isAnnotatedWith(JMoleculesTypes.AT_DOMAIN_EVENT));

		return classes.that(isEvent).stream() //
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

		return classes.stream() //
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

	/**
	 * Returns the current module's immediate parent module, if present.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Optional<ApplicationModule> getParentModule(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		var byPackageDepth = comparing(ApplicationModule::getBasePackage, JavaPackage.reverse());

		return modules.stream()
				.filter(it -> basePackage.isSubPackageOf(it.getBasePackage()))
				.sorted(byPackageDepth)
				.findFirst();
	}

	/**
	 * Returns the {@link ApplicationModule}s directly nested inside the current one.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Collection<ApplicationModule> getDirectlyNestedModules(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		return doGetNestedModules(modules, false);
	}

	/**
	 * Returns all of the current {@link ApplicationModule}'s nested {@link ApplicationModule}s including ones contained
	 * in nested modules in turn.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Collection<ApplicationModule> getNestedModules(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		return doGetNestedModules(modules, true);
	}

	/**
	 * @return the classes
	 */
	Classes getClasses() {
		return classes;
	}

	/**
	 * Returns all types internal to the module.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.4
	 */
	public Collection<JavaClass> getInternalTypes() {

		return classes.stream()
				.filter(Predicate.not(this::isExposed))
				.toList();
	}

	private String getQualifiedName(NamedInterface namedInterface) {
		return namedInterface.getQualifiedName(getIdentifier());
	}

	private Collection<ApplicationModule> doGetNestedModules(ApplicationModules modules, boolean recursive) {

		var result = modules.stream()
				.filter(it -> it.getParentModule(modules).filter(this::equals).isPresent());

		if (recursive) {
			result = result.flatMap(it -> Stream.concat(Stream.of(it), it.getNestedModules(modules).stream()));
		}

		return result.toList();
	}

	private List<JavaClass> findArchitecturallyEvidentType(Predicate<ArchitecturallyEvidentType> selector) {

		var springBeansInternal = getSpringBeansInternal();

		return classes.stream()
				.map(it -> ArchitecturallyEvidentType.of(it, springBeansInternal))
				.filter(selector)
				.map(ArchitecturallyEvidentType::getType)
				.toList();
	}

	private Stream<QualifiedDependency> getModuleDependenciesOf(JavaClass type, ApplicationModules modules) {

		var evidentType = ArchitecturallyEvidentType.of(type, getSpringBeansInternal());

		var injections = QualifiedDependency.fromType(evidentType) //
				.filter(it -> isDependencyToOtherModule(it.getTarget(), modules)); //

		var directDependencies = type.getDirectDependenciesFromSelf().stream() //
				.filter(it -> JavaTypes.IS_NOT_CORE_JAVA_TYPE.test(it.getTargetClass())) //
				.filter(it -> isDependencyToOtherModule(it.getTargetClass(), modules)) //
				.map(QualifiedDependency::new);

		return Stream.concat(injections, directDependencies).distinct();
	}

	private boolean isDependencyToOtherModule(JavaClass dependency, ApplicationModules modules) {
		return modules.contains(dependency) && !contains(dependency);
	}

	private Classes findAggregateRoots(Classes source) {

		return source.stream() //
				.map(it -> ArchitecturallyEvidentType.of(it, getSpringBeansInternal()))
				.filter(ArchitecturallyEvidentType::isAggregateRoot) //
				.map(ArchitecturallyEvidentType::getType) //
				.collect(Classes.toClasses());
	}

	private Stream<ApplicationModuleDependency> getDependencies(ApplicationModules modules,
			DependencyTraversal traversal) {

		return getAllModuleDependencies(modules) //
				.filter(traversal::include) //
				.distinct() //
				.flatMap(it -> DefaultApplicationModuleDependency.of(it, modules)) //
				.distinct() //
				.flatMap(it -> resolveRecursively(modules, it, traversal)); //
	}

	private static Stream<ApplicationModuleDependency> resolveRecursively(ApplicationModules modules,
			ApplicationModuleDependency dependency, DependencyTraversal seen) {

		if (!seen.register(dependency.getTargetModule())) {
			return Stream.of(dependency);
		}

		var tail = seen.hasDepth(DependencyDepth.ALL)
				? dependency.getTargetModule().getDependencies(modules, seen).distinct()
				: Stream.<ApplicationModuleDependency> empty();

		return Stream.concat(Stream.of(dependency), tail);
	}

	private static Classes filterSpringBeans(Classes source) {

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

	private static String toBulletList(Stream<ApplicationModule> modules,
			Stream<ApplicationModule> bootstrapDependencies) {

		var bootstrapIdentifiers = bootstrapDependencies
				.map(ApplicationModule::getIdentifier)
				.map(ApplicationModuleIdentifier::toString)
				.toList();

		return modules.sorted()
				.map(ApplicationModule::getIdentifier)
				.map(ApplicationModuleIdentifier::toString)
				.map(it -> (bootstrapIdentifiers.contains(it) ? "  + " : "  - ").concat(it))
				.collect(Collectors.joining("\n", "\n", ""));
	}

	/**
	 * Describes a dependency explicitly declared as allowed for an {@link ApplicationModule}.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.4.2, package private before
	 */
	public static class AllowedDependency {

		private static final String INVALID_EXPLICIT_MODULE_DEPENDENCY = "Invalid explicit module dependency in %s! No module found with name '%s'.";
		private static final String INVALID_NAMED_INTERFACE_DECLARATION = "No named interface named '%s' found! Original dependency declaration: %s -> %s.";
		private static final String WILDCARD = "*";

		private final ApplicationModule target;
		private final @Nullable NamedInterface namedInterface;

		/**
		 * Creates a new {@link AllowedDependency} for the given {@link ApplicationModule} and {@link NamedInterface}.
		 *
		 * @param target must not be {@literal null}.
		 * @param namedInterface can be {@literal null}.
		 */
		private AllowedDependency(ApplicationModule target, @Nullable NamedInterface namedInterface) {

			Assert.notNull(target, "Target ApplicationModule must not be null!");

			this.target = target;
			this.namedInterface = namedInterface;
		}

		/**
		 * Creates an {@link AllowedDependency} to the module and optionally named interface defined by the given
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
		static AllowedDependency of(String identifier, ApplicationModule source,
				ApplicationModules modules) {

			Assert.hasText(identifier, "Module dependency identifier must not be null or empty!");

			var segments = identifier.split("::");
			var targetModuleName = segments[0].trim();
			var namedInterfaceName = segments.length > 1 ? segments[1].trim() : null;

			var target = modules.getModuleByName(targetModuleName)
					.orElseThrow(() -> new IllegalArgumentException(
							INVALID_EXPLICIT_MODULE_DEPENDENCY.formatted(source.getIdentifier(), targetModuleName)));

			if (WILDCARD.equals(namedInterfaceName)) {
				return new AllowedDependency(target, null);
			}

			var namedInterfaces = target.getNamedInterfaces();
			var namedInterface = namedInterfaceName == null
					? namedInterfaces.getUnnamedInterface()
					: namedInterfaces.getByName(namedInterfaceName)
							.orElseThrow(() -> new IllegalArgumentException(
									INVALID_NAMED_INTERFACE_DECLARATION.formatted(namedInterfaceName, source.getIdentifier(),
											identifier)));

			return new AllowedDependency(target, namedInterface);
		}

		/**
		 * Creates a new {@link AllowedDependency} to the unnamed interface of the given {@link ApplicationModule}.
		 *
		 * @param module must not be {@literal null}.
		 * @return
		 */
		static AllowedDependency to(ApplicationModule module) {

			Assert.notNull(module, "ApplicationModule must not be null!");

			return new AllowedDependency(module, module.getNamedInterfaces().getUnnamedInterface());
		}

		/**
		 * Returns the target module of the dependency.
		 *
		 * @return will never be {@literal null}.
		 */
		public ApplicationModule getTargetModule() {
			return target;
		}

		/**
		 * Returns the {@link NamedInterface} declared as valid target if declared.
		 *
		 * @return can be {@literal null}.
		 */
		public @Nullable NamedInterface getTargetNamedInterface() {
			return namedInterface;
		}

		/**
		 * Returns whether the {@link AllowedDependency} contains the given {@link JavaClass}.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		boolean contains(JavaClass type) {

			Assert.notNull(type, "Type must not be null!");

			return namedInterface == null
					? target.getNamedInterfaces().containsInExplicitInterface(type)
					: namedInterface.contains(type);
		}

		/**
		 * Returns whether the {@link AllowedDependency} contains the given {@link Class}.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		boolean contains(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");

			return namedInterface == null
					? target.getNamedInterfaces().containsInExplicitInterface(type)
					: namedInterface.contains(type);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {

			var result = target.getIdentifier().toString();
			var ni = namedInterface;

			if (ni == null) {
				return result + " :: " + WILDCARD;
			}

			if (ni.isUnnamed()) {
				return result;
			}

			return result + " :: " + ni.getName();
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(@Nullable Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof AllowedDependency that)) {
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
	 * A collection wrapper for {@link AllowedDependency} instances.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.4.2, previously package private
	 */
	public static class AllowedDependencies implements Iterable<AllowedDependency> {

		private static final String OPEN_TOKEN = "¯\\_(ツ)_/¯";

		private final List<AllowedDependency> dependencies;
		private final boolean closed;

		static boolean isOpen(List<String> AllowedDependencies) {
			return AllowedDependencies.size() == 1 && AllowedDependencies.get(0).equals(OPEN_TOKEN);
		}

		static AllowedDependencies open() {
			return new AllowedDependencies(Collections.emptyList(), false);
		}

		static AllowedDependencies closed(List<AllowedDependency> dependencies) {
			return new AllowedDependencies(dependencies, true);
		}

		/**
		 * Creates a new {@link AllowedDependencies} for the given {@link List} of {@link AllowedDependency}.
		 *
		 * @param dependencies must not be {@literal null}.
		 */
		private AllowedDependencies(List<AllowedDependency> dependencies, boolean closed) {

			Assert.notNull(dependencies, "Dependencies must not be null!");

			this.dependencies = dependencies;
			this.closed = closed;
		}

		/**
		 * Returns whether we have any allowed dependencies at all.
		 */
		public boolean isEmpty() {
			return dependencies.isEmpty();
		}

		/**
		 * Returns all {@link AllowedDependency} instances as a {@link Stream}.
		 *
		 * @return will never be {@literal null}.
		 */
		public Stream<AllowedDependency> stream() {
			return dependencies.stream();
		}

		/**
		 * Returns whether the given {@link JavaClass} is a valid dependency.
		 *
		 * @param type must not be {@literal null}.
		 * @return
		 */
		boolean isAllowedDependency(JavaClass type) {
			return isAllowedDependency(it -> it.contains(type));
		}

		boolean isAllowedDependency(Class<?> type) {
			return isAllowedDependency(it -> it.contains(type));
		}

		private boolean isAllowedDependency(Predicate<AllowedDependency> predicate) {

			Assert.notNull(predicate, "Predicate must not be null!");

			return closed ? !dependencies.isEmpty() && contains(predicate) : dependencies.isEmpty() || contains(predicate);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Iterable#iterator()
		 */
		@Override
		public Iterator<AllowedDependency> iterator() {
			return dependencies.iterator();
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {

			return dependencies.isEmpty() //
					? "none" //
					: dependencies.stream().map(AllowedDependency::toString).collect(Collectors.joining(", "));
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(@Nullable Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof AllowedDependencies that)) {
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
		private boolean contains(Predicate<AllowedDependency> condition) {

			Assert.notNull(condition, "Condition must not be null!");

			return dependencies.stream().anyMatch(condition);
		}
	}

	static class QualifiedDependency {

		private static final List<String> INJECTION_TYPES = Arrays.asList(AT_AUTOWIRED, AT_RESOURCE, AT_INJECT);

		private static final String INVALID_SUB_MODULE_REFERENCE = "Invalid sub-module reference from module '%s' to module '%s' (via %s -> %s)!";
		private static final String INTERNAL_REFERENCE = "Module '%s' depends on non-exposed type %s within module '%s'!";

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

		static Stream<QualifiedDependency> fromCodeUnitParameter(JavaCodeUnit codeUnit, JavaClass parameter) {

			if (JavaTypes.IS_CORE_JAVA_TYPE.test(parameter)) {
				return Stream.empty();
			}

			var description = createDescription(codeUnit, parameter, "parameter");
			var type = DependencyType.forCodeUnit(codeUnit) //
					.defaultOr(() -> DependencyType.forParameter(parameter));

			return Stream.of(new QualifiedDependency(codeUnit.getOwner(), parameter, description, type));
		}

		static Stream<QualifiedDependency> fromCodeUnitReturnType(JavaCodeUnit codeUnit) {

			var returnType = codeUnit.getRawReturnType();

			if (JavaTypes.IS_CORE_JAVA_TYPE.test(returnType)) {
				return Stream.empty();
			}

			var description = createDescription(codeUnit, codeUnit.getRawReturnType(), "return type");

			return Stream.of(new QualifiedDependency(codeUnit.getOwner(), codeUnit.getRawReturnType(), description,
					DependencyType.DEFAULT));
		}

		static Stream<QualifiedDependency> fromType(ArchitecturallyEvidentType type) {

			var source = type.getType();

			return Stream.concat(Stream.concat(fromConstructorOf(type), fromMethodsOf(source)), fromFieldsOf(source));
		}

		static Stream<QualifiedDependency> allFrom(JavaCodeUnit codeUnit) {

			var parameterDependencies = codeUnit.getRawParameterTypes()//
					.stream() //
					.filter(JavaTypes.IS_NOT_CORE_JAVA_TYPE) //
					.flatMap(it -> fromCodeUnitParameter(codeUnit, it));

			var returnType = fromCodeUnitReturnType(codeUnit);

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

		boolean hasAnyType(Collection<DependencyType> candidates) {
			return candidates.contains(type);
		}

		Violations isValidDependencyWithin(ApplicationModules modules) {

			var originModule = getExistingModuleOf(source, modules);
			var targetModule = getExistingModuleOf(target, modules);
			var violations = Violations.NONE;

			if (originModule.equals(targetModule)) {
				return violations;
			}

			var allowed = originModule.getAllowedDependencies(modules);

			// Check explicitly defined allowed targets
			if (!allowed.isAllowedDependency(target)) {

				var targetNamedInterfaces = targetModule.getNamedInterfaces()
						.getNamedInterfacesContaining(target)
						.filter(NamedInterface::isNamed)
						.toList();

				var targetString = targetNamedInterfaces.isEmpty()
						? "module '%s'".formatted(targetModule.getIdentifier())
						: "named interface(s) '%s'".formatted(
								targetNamedInterfaces.stream()
										.map(targetModule::getQualifiedName)
										.collect(Collectors.joining(", ")));

				var message = "Module '%s' depends on %s via %s -> %s. Allowed targets: %s." //
						.formatted(originModule.getIdentifier(), targetString, source.getName(), target.getName(),
								allowed.toString());

				return violations.and(new Violation(message));
			}

			// No explicitly allowed dependencies - check for general access

			if (targetModule.isOpen()) {
				return violations;
			}

			if (originModule.containsTypeInAnyParent(target, modules)) {
				return violations;
			}

			if (!targetModule.isExposed(target)) {

				var violationText = INTERNAL_REFERENCE
						.formatted(originModule.getIdentifier(), target.getName(), targetModule.getIdentifier());

				return violations.and(new Violation(violationText + lineSeparator() + description));
			}

			// Parent child relationships

			if (!haveSameParentOrDirectParentRelationship(originModule, targetModule, modules)) {

				var violationText = INVALID_SUB_MODULE_REFERENCE
						.formatted(originModule.getIdentifier(), targetModule.getIdentifier(),
								FormattableType.of(source).getAbbreviatedFullName(originModule),
								FormattableType.of(target).getAbbreviatedFullName(targetModule));

				return violations.and(new Violation(violationText));
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
			return type.format(FormattableType.of(source), FormattableType.of(target));
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
							.filter(Predicate.not(JavaTypes.IS_CORE_JAVA_TYPE))
							.map(parameter -> {
								return source.isInjectable() && !source.isConfigurationProperties()
										? new InjectionDependency(it, parameter)
										: new QualifiedDependency(type, parameter, createDescription(it, parameter, "parameter"),
												DependencyType.DEFAULT);
							}));
		}

		private static Stream<QualifiedDependency> fromFieldsOf(JavaClass source) {

			return source.getAllFields().stream() //
					.filter(it -> JavaTypes.IS_NOT_CORE_JAVA_TYPE.test(it.getRawType())) //
					.filter(QualifiedDependency::isInjectionPoint) //
					.map(field -> new InjectionDependency(field, field.getRawType()));
		}

		private static Stream<QualifiedDependency> fromMethodsOf(JavaClass source) {

			if (JavaTypes.IS_CORE_JAVA_TYPE.test(source)) {
				return Stream.empty();
			}

			var methods = source.getAllMethods().stream() //
					.filter(it -> !it.getOwner().isEquivalentTo(Object.class)) //
					.collect(Collectors.toSet());

			if (methods.isEmpty()) {
				return Stream.empty();
			}

			var returnTypes = methods.stream() //
					.filter(it -> !it.getRawReturnType().isPrimitive()) //
					.filter(it -> JavaTypes.IS_NOT_CORE_JAVA_TYPE.test(it.getRawReturnType())) //
					.flatMap(it -> fromCodeUnitReturnType(it));

			var injectionMethods = methods.stream() //
					.filter(QualifiedDependency::isInjectionPoint) //
					.collect(Collectors.toSet());

			var methodInjections = injectionMethods.stream() //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.filter(JavaTypes.IS_NOT_CORE_JAVA_TYPE) //
							.map(parameter -> new InjectionDependency(it, parameter)));

			var otherMethods = methods.stream() //
					.filter(it -> !injectionMethods.contains(it)) //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.filter(JavaTypes.IS_NOT_CORE_JAVA_TYPE) //
							.flatMap(parameter -> fromCodeUnitParameter(it, parameter)));

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

		private static boolean haveSameParentOrDirectParentRelationship(ApplicationModule source, ApplicationModule target,
				ApplicationModules modules) {

			var sourceParent = modules.getParentOf(source);
			var targetParent = modules.getParentOf(target);

			// Top-level modules
			return targetParent.isEmpty()

					// One is parent of the other
					|| hasValue(sourceParent, target)
					|| hasValue(targetParent, source)

					// Same immediate parent
					|| sourceParent.flatMap(it -> targetParent.filter(it::equals)).isPresent();
		}

		private static <T> boolean hasValue(Optional<T> optional, T expected) {
			return optional.filter(expected::equals).isPresent();
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

				violations = violations.and(new Violation(
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

	/**
	 * A helper type to keep track of a traversal configuration and seen {@link ApplicationModule}s.
	 *
	 * @author Oliver Drotbohm
	 */
	private static class DependencyTraversal {

		private final Set<ApplicationModule> seen = new HashSet<>();
		private final DependencyDepth depth;
		private final Set<DependencyType> types;

		DependencyTraversal(DependencyDepth depth, DependencyType... types) {

			this.depth = depth;
			this.types = Set.of(types);
		}

		boolean include(QualifiedDependency dependency) {
			return types.isEmpty() || dependency.hasAnyType(types);
		}

		boolean hasDepth(DependencyDepth depth) {
			return this.depth == depth;
		}

		boolean register(ApplicationModule module) {

			if (seen.contains(module)) {
				return false;
			}

			seen.add(module);

			return true;
		}
	}

	private static class DefaultApplicationModuleDependency
			implements ApplicationModuleDependency {

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
		static Stream<DefaultApplicationModuleDependency> of(QualifiedDependency dependency, ApplicationModules modules) {

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
