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
import static java.lang.System.*;
import static org.springframework.modulith.model.Types.*;
import static org.springframework.modulith.model.Types.JavaXTypes.*;
import static org.springframework.modulith.model.Types.SpringDataTypes.*;
import static org.springframework.modulith.model.Types.SpringTypes.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.modulith.model.Types.JMoleculesTypes;
import org.springframework.modulith.model.Types.SpringTypes;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.SourceCodeLocation;
import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * @author Oliver Gierke
 */
@EqualsAndHashCode(doNotUseGetters = true)
public class Module {

	private final @Getter JavaPackage basePackage;
	private final ModuleInformation information;
	private final @Getter NamedInterfaces namedInterfaces;
	private final boolean useFullyQualifiedModuleNames;

	private final Supplier<Classes> springBeans;
	private final Supplier<Classes> entities;
	private final Supplier<List<EventType>> publishedEvents;

	Module(JavaPackage basePackage, boolean useFullyQualifiedModuleNames) {

		this.basePackage = basePackage;
		this.information = ModuleInformation.of(basePackage);
		this.namedInterfaces = NamedInterfaces.discoverNamedInterfaces(basePackage);
		this.useFullyQualifiedModuleNames = useFullyQualifiedModuleNames;

		this.springBeans = Suppliers.memoize(() -> filterSpringBeans(basePackage));
		this.entities = Suppliers.memoize(() -> findEntities(basePackage));
		this.publishedEvents = Suppliers.memoize(() -> findPublishedEvents());
	}

	public String getName() {
		return useFullyQualifiedModuleNames ? basePackage.getName() : basePackage.getLocalName();
	}

	public String getDisplayName() {
		return information.getDisplayName();
	}

	public List<Module> getDependencies(Modules modules, DependencyType... type) {

		return getAllModuleDependencies(modules) //
				.filter(it -> type.length == 0 ? true : Arrays.stream(type).anyMatch(it::hasType)) //
				.map(it -> modules.getModuleByType(it.target)) //
				.distinct() //
				.flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty)) //
				.collect(Collectors.toList());
	}

	/**
	 * Returns all event types the current module exposes an event listener for.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 */
	public List<JavaClass> getEventsListenedTo(Modules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		return getAllModuleDependencies(modules) //
				.filter(it -> it.type == DependencyType.EVENT_LISTENER) //
				.map(ModuleDependency::getTarget) //
				.collect(Collectors.toList());
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
	 * Returns all types that are considered aggregate roots.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<JavaClass> getAggregateRoots() {

		return entities.get().stream() //
				.map(it -> ArchitecturallyEvidentType.of(it, getSpringBeansInternal())) //
				.filter(ArchitecturallyEvidentType::isAggregateRoot) //
				.map(ArchitecturallyEvidentType::getType) //
				.flatMap(this::resolveModuleSuperTypes) //
				.distinct() //
				.collect(Collectors.toList());
	}

	/**
	 * Returns all types that are considered aggregate roots.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 * @deprecated since 1.3, use {@link #getAggregateRoots()} instead.
	 */
	@Deprecated
	public List<JavaClass> getAggregateRoots(Modules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		return getAggregateRoots();
	}

	/**
	 * Returns all modules that contain types which the types of the current module depend on.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 */
	public Stream<Module> getBootstrapDependencies(Modules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		return getBootstrapDependencies(modules, DependencyDepth.IMMEDIATE);
	}

	public Stream<Module> getBootstrapDependencies(Modules modules, DependencyDepth depth) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.notNull(depth, "Dependency depth must not be null!");

		return streamDependencies(modules, depth);
	}

	/**
	 * Returns all {@link JavaPackage} for the current module including the ones by its dependencies.
	 *
	 * @param modules must not be {@literal null}.
	 * @param depth must not be {@literal null}.
	 * @return
	 */
	public Stream<JavaPackage> getBasePackages(Modules modules, DependencyDepth depth) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.notNull(depth, "Dependency depth must not be null!");

		Stream<Module> dependencies = streamDependencies(modules, depth);

		return Stream.concat(Stream.of(this), dependencies) //
				.map(Module::getBasePackage);
	}

	public List<SpringBean> getSpringBeans() {
		return getSpringBeansInternal().stream() //
				.map(it -> SpringBean.of(it, this)) //
				.collect(Collectors.toList());
	}

	Classes getSpringBeansInternal() {
		return springBeans.get();
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
	 * @since 1.1
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

	public void verifyDependencies(Modules modules) {
		detectDependencies(modules).throwIfPresent();
	}

	public Violations detectDependencies(Modules modules) {

		return getAllModuleDependencies(modules) //
				.map(it -> it.isValidDependencyWithin(modules)) //
				.reduce(Violations.NONE, Violations::and);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return toString(null);
	}

	public String toString(@Nullable Modules modules) {

		StringBuilder builder = new StringBuilder("## ").append(getDisplayName()).append(" ##\n");
		builder.append("> Logical name: ").append(getName()).append('\n');
		builder.append("> Base package: ").append(basePackage.getName()).append('\n');

		if (namedInterfaces.hasExplicitInterfaces()) {

			builder.append("> Named interfaces:\n");

			namedInterfaces.forEach(it -> builder.append("  + ") //
					.append(it.toString()) //
					.append('\n'));
		}

		if (modules != null) {

			List<Module> dependencies = getBootstrapDependencies(modules).collect(Collectors.toList());

			builder.append("> Direct module dependencies: ");
			builder.append(dependencies.isEmpty() ? "none"
					: dependencies.stream().map(Module::getName).collect(Collectors.joining(", ")));
			builder.append('\n');
		}

		Classes beans = getSpringBeansInternal();

		if (beans.isEmpty()) {

			builder.append("> Spring beans: none\n");

		} else {

			builder.append("> Spring beans:\n");
			beans.forEach(it -> builder.append("  ") //
					.append(Classes.format(it, basePackage.getName()))//
					.append('\n'));
		}

		return builder.toString();
	}

	/**
	 * Returns all allowed module dependencies, either explicitly declared or defined as shared on the given
	 * {@link Modules} instance.
	 *
	 * @param modules must not be {@literal null}.
	 * @return
	 */
	List<Module> getAllowedDependencies(Modules modules) {

		Assert.notNull(modules, "Modules must not be null!");

		List<String> allowedDependencyNames = information.getAllowedDependencies();

		if (allowedDependencyNames.isEmpty()) {
			return Collections.emptyList();
		}

		Stream<Module> explicitlyDeclaredModules = allowedDependencyNames.stream() //
				.map(modules::getModuleByName) //
				.flatMap(it -> it.map(Stream::of).orElse(Stream.empty()));

		return Stream.concat(explicitlyDeclaredModules, modules.getSharedModules().stream()) //
				.distinct() //
				.collect(Collectors.toList());
	}

	/**
	 * Returns whether the given module contains a type with the given simple or fully qualified name.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 * @return
	 * @since 1.1
	 */
	boolean contains(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or empty!");

		return getType(candidate).isPresent();
	}

	private List<EventType> findPublishedEvents() {

		DescribedPredicate<JavaClass> isEvent = implement(JMoleculesTypes.DOMAIN_EVENT) //
				.or(isAnnotatedWith(JMoleculesTypes.AT_DOMAIN_EVENT));

		return basePackage.that(isEvent).stream() //
				.map(EventType::new)
				.collect(Collectors.toList());
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

	private Stream<ModuleDependency> getAllModuleDependencies(Modules modules) {

		return basePackage.stream() //
				.flatMap(it -> getModuleDependenciesOf(it, modules));
	}

	private Stream<Module> streamDependencies(Modules modules, DependencyDepth depth) {

		switch (depth) {

			case NONE:
				return Stream.empty();
			case IMMEDIATE:
				return getDirectModuleDependencies(modules);
			case ALL:
			default:
				return getDirectModuleDependencies(modules) //
						.flatMap(it -> Stream.concat(Stream.of(it), it.streamDependencies(modules, DependencyDepth.ALL))) //
						.distinct();
		}
	}

	private Stream<Module> getDirectModuleDependencies(Modules modules) {

		return getSpringBeansInternal().stream() //
				.flatMap(it -> ModuleDependency.fromType(it)) //
				.filter(it -> isDependencyToOtherModule(it.target, modules)) //
				.map(it -> modules.getModuleByType(it.target)) //
				.distinct() //
				.flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty));
	}

	private Stream<ModuleDependency> getModuleDependenciesOf(JavaClass type, Modules modules) {

		Stream<ModuleDependency> injections = ModuleDependency.fromType(type) //
				.filter(it -> isDependencyToOtherModule(it.getTarget(), modules)); //

		Stream<ModuleDependency> directDependencies = type.getDirectDependenciesFromSelf().stream() //
				.filter(it -> isDependencyToOtherModule(it.getTargetClass(), modules)) //
				.map(ModuleDependency::new);

		return Stream.concat(injections, directDependencies).distinct();
	}

	private boolean isDependencyToOtherModule(JavaClass dependency, Modules modules) {
		return modules.contains(dependency) && !contains(dependency);
	}

	private Classes findEntities(JavaPackage source) {

		return source.stream() //
				.map(it -> ArchitecturallyEvidentType.of(it, getSpringBeansInternal()))
				.filter(ArchitecturallyEvidentType::isEntity) //
				.map(ArchitecturallyEvidentType::getType).collect(Classes.toClasses());
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

		return coreComponents //
				.and(repositories) //
				.and(configurationProperties) //
				.and(collect.getOrDefault(true, Collections.emptyList())) //
				.and(collect.getOrDefault(false, Collections.emptyList()));
	}

	private static Predicate<JavaClass> hasSimpleOrFullyQualifiedName(String candidate) {
		return it -> it.getSimpleName().equals(candidate) || it.getFullName().equals(candidate);
	}

	public enum DependencyDepth {

		NONE,

		IMMEDIATE,

		ALL;
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor
	static class ModuleDependency {

		private static final List<String> INJECTION_TYPES = Arrays.asList(//
				AT_AUTOWIRED, AT_RESOURCE, AT_INJECT);

		private final @NonNull @Getter JavaClass origin, target;
		private final @NonNull String description;
		private final @NonNull DependencyType type;

		ModuleDependency(Dependency dependency) {
			this(dependency.getOriginClass(), //
					dependency.getTargetClass(), //
					dependency.getDescription(), //
					DependencyType.forDependency(dependency));
		}

		boolean hasType(DependencyType type) {
			return this.type.equals(type);
		}

		Violations isValidDependencyWithin(Modules modules) {

			Module originModule = getExistingModuleOf(origin, modules);
			Module targetModule = getExistingModuleOf(target, modules);

			List<Module> allowedTargets = originModule.getAllowedDependencies(modules);
			Violations violations = Violations.NONE;

			if (!allowedTargets.isEmpty() && !allowedTargets.contains(targetModule)) {

				String allowedTargetsString = allowedTargets.stream() //
						.map(Module::getName) //
						.collect(Collectors.joining(", "));

				String message = String.format("Module '%s' depends on module '%s' via %s -> %s. Allowed target modules: %s.",
						originModule.getName(), targetModule.getName(), origin.getName(), target.getName(), allowedTargetsString);

				violations = violations.and(new IllegalStateException(message));
			}

			if (!targetModule.isExposed(target)) {

				String violationText = String.format("Module '%s' depends on non-exposed type %s within module '%s'!",
						originModule.getName(), target.getName(), targetModule.getName());

				violations = violations.and(new IllegalStateException(violationText + lineSeparator() + description));
			}

			return violations;
		}

		Module getExistingModuleOf(JavaClass javaClass, Modules modules) {

			Optional<Module> module = modules.getModuleByType(javaClass);

			return module.orElseThrow(() -> new IllegalStateException(
					String.format("Origin/Target of a %s should always be within a module, but %s is not",
							getClass().getSimpleName(), javaClass.getName())));
		}

		static ModuleDependency fromCodeUnitParameter(JavaCodeUnit codeUnit, JavaClass parameter) {

			String description = createDescription(codeUnit, parameter, "parameter");

			DependencyType type = DependencyType.forCodeUnit(codeUnit) //
					.or(() -> DependencyType.forParameter(parameter));

			return new ModuleDependency(codeUnit.getOwner(), parameter, description, type);
		}

		static ModuleDependency fromCodeUnitReturnType(JavaCodeUnit codeUnit) {

			String description = createDescription(codeUnit, codeUnit.getRawReturnType(), "return type");

			return new ModuleDependency(codeUnit.getOwner(), codeUnit.getRawReturnType(), description,
					DependencyType.DEFAULT);
		}

		static Stream<ModuleDependency> fromType(JavaClass source) {
			return Stream.concat(Stream.concat(fromConstructorOf(source), fromMethodsOf(source)), fromFieldsOf(source));
		}

		private static Stream<ModuleDependency> fromConstructorOf(JavaClass source) {

			Set<JavaConstructor> constructors = source.getConstructors();

			return constructors.stream() //
					.filter(it -> constructors.size() == 1 || isInjectionPoint(it)) //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.map(parameter -> new InjectionModuleDependency(source, parameter, it)));
		}

		private static Stream<ModuleDependency> fromFieldsOf(JavaClass source) {

			Stream<ModuleDependency> fieldInjections = source.getAllFields().stream() //
					.filter(ModuleDependency::isInjectionPoint) //
					.map(field -> new InjectionModuleDependency(source, field.getRawType(), field));

			return fieldInjections;
		}

		private static Stream<ModuleDependency> fromMethodsOf(JavaClass source) {

			Set<JavaMethod> methods = source.getAllMethods().stream() //
					.filter(it -> !it.getOwner().isEquivalentTo(Object.class)) //
					.collect(Collectors.toSet());

			if (methods.isEmpty()) {
				return Stream.empty();
			}

			Stream<ModuleDependency> returnTypes = methods.stream() //
					.filter(it -> !it.getRawReturnType().isPrimitive()) //
					.filter(it -> !it.getRawReturnType().getPackageName().startsWith("java")) //
					.map(it -> fromCodeUnitReturnType(it));

			Set<JavaMethod> injectionMethods = methods.stream() //
					.filter(ModuleDependency::isInjectionPoint) //
					.collect(Collectors.toSet());

			Stream<ModuleDependency> methodInjections = injectionMethods.stream() //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.map(parameter -> new InjectionModuleDependency(source, parameter, it)));

			Stream<ModuleDependency> otherMethods = methods.stream() //
					.filter(it -> !injectionMethods.contains(it)) //
					.flatMap(it -> it.getRawParameterTypes().stream() //
							.map(parameter -> fromCodeUnitParameter(it, parameter)));

			return Stream.concat(Stream.concat(methodInjections, otherMethods), returnTypes);
		}

		static Stream<ModuleDependency> allFrom(JavaCodeUnit codeUnit) {

			Stream<ModuleDependency> parameterDependencies = codeUnit.getRawParameterTypes()//
					.stream() //
					.map(it -> fromCodeUnitParameter(codeUnit, it));

			Stream<ModuleDependency> returnType = Stream.of(fromCodeUnitReturnType(codeUnit));

			return Stream.concat(parameterDependencies, returnType);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return type.format(FormatableJavaClass.of(origin), FormatableJavaClass.of(target));
		}

		private static String createDescription(JavaMember codeUnit, JavaClass declaringElement,
				String declarationDescription) {

			String type = declaringElement.getSimpleName();

			String codeUnitDescription = JavaConstructor.class.isInstance(codeUnit) //
					? String.format("%s", declaringElement.getSimpleName()) //
					: String.format("%s.%s", declaringElement.getSimpleName(), codeUnit.getName());

			if (JavaCodeUnit.class.isInstance(codeUnit)) {
				codeUnitDescription = String.format("%s(%s)", codeUnitDescription,
						JavaCodeUnit.class.cast(codeUnit).getRawParameterTypes().stream() //
								.map(JavaClass::getSimpleName) //
								.collect(Collectors.joining(", ")));
			}

			String annotations = codeUnit.getAnnotations().stream() //
					.filter(it -> INJECTION_TYPES.contains(it.getRawType().getName())) //
					.map(it -> "@" + it.getRawType().getSimpleName()) //
					.collect(Collectors.joining(" ", "", " "));

			annotations = StringUtils.hasText(annotations) ? annotations : "";

			String declaration = declarationDescription + " " + annotations + codeUnitDescription;
			String location = SourceCodeLocation.of(codeUnit.getOwner(), 0).toString();

			return String.format("%s declares %s in %s", type, declaration, location);
		}

		private static boolean isInjectionPoint(JavaMember unit) {
			return INJECTION_TYPES.stream().anyMatch(type -> unit.isAnnotatedWith(type));
		}
	}

	private static class InjectionModuleDependency extends ModuleDependency {

		private final JavaMember member;
		private final boolean isConfigurationClass;

		/**
		 * @param origin
		 * @param target
		 * @param member
		 */
		public InjectionModuleDependency(JavaClass origin, JavaClass target, JavaMember member) {

			super(origin, target, ModuleDependency.createDescription(member, origin, getDescriptionFor(member)),
					DependencyType.USES_COMPONENT);

			this.member = member;
			this.isConfigurationClass = isConfiguration().apply(origin);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.ModuleDependency#isValidDependencyWithin(org.springframework.modulith.model.Modules)
		 */
		@Override
		Violations isValidDependencyWithin(Modules modules) {

			Violations violations = super.isValidDependencyWithin(modules);

			if (JavaField.class.isInstance(member) && !isConfigurationClass) {

				Module module = getExistingModuleOf(member.getOwner(), modules);

				violations = violations.and(new IllegalStateException(
						String.format("Module %s uses field injection in %s. Prefer constructor injection instead!",
								module.getDisplayName(), member.getFullName())));
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

	public enum DependencyType {

		/**
		 * Indicates that the module depends on the other one by a component dependency, i.e. that other module needs to be
		 * bootstrapped to run the source module.
		 */
		USES_COMPONENT {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
			 */
			@Override
			public String format(FormatableJavaClass source, FormatableJavaClass target) {
				return String.format("Component %s using %s", source.getAbbreviatedFullName(), target.getAbbreviatedFullName());
			}
		},

		/**
		 * Indicates that the module refers to an entity of the other.
		 */
		ENTITY {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
			 */
			@Override
			public String format(FormatableJavaClass source, FormatableJavaClass target) {
				return String.format("Entity %s depending on %s", source.getAbbreviatedFullName(),
						target.getAbbreviatedFullName());
			}
		},

		/**
		 * Indicates that the module depends on the other by declaring an event listener for an event exposed by the other
		 * module. Thus, the target module does not have to be bootstrapped to run the source one.
		 */
		EVENT_LISTENER {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
			 */
			@Override
			public String format(FormatableJavaClass source, FormatableJavaClass target) {
				return String.format("%s listening to events of type %s", source.getAbbreviatedFullName(),
						target.getAbbreviatedFullName());
			}
		},

		DEFAULT {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.model.Module.DependencyType#or(com.tngtech.archunit.thirdparty.com.google.common.base.Supplier)
			 */
			@Override
			public DependencyType or(Supplier<DependencyType> supplier) {
				return supplier.get();
			}

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
			 */
			@Override
			public String format(FormatableJavaClass source, FormatableJavaClass target) {
				return String.format("%s depending on %s", source.getAbbreviatedFullName(), target.getAbbreviatedFullName());
			}
		};

		public static DependencyType forParameter(JavaClass type) {
			return type.isAnnotatedWith("javax.persistence.Entity") ? ENTITY : DEFAULT;
		}

		public static DependencyType forCodeUnit(JavaCodeUnit codeUnit) {
			return Types.isAnnotatedWith(SpringTypes.AT_EVENT_LISTENER).apply(codeUnit) //
					|| Types.isAnnotatedWith(JMoleculesTypes.AT_DOMAIN_EVENT_HANDLER).apply(codeUnit) //
							? EVENT_LISTENER
							: DEFAULT;
		}

		public static DependencyType forDependency(Dependency dependency) {
			return forParameter(dependency.getTargetClass());
		}

		public abstract String format(FormatableJavaClass source, FormatableJavaClass target);

		public DependencyType or(Supplier<DependencyType> supplier) {
			return this;
		}

		/**
		 * Returns all {@link DependencyType}s except the given ones.
		 *
		 * @param types must not be {@literal null}.
		 * @return
		 */
		public static Stream<DependencyType> allBut(Collection<DependencyType> types) {

			Assert.notNull(types, "Types must not be null!");

			Predicate<DependencyType> isIncluded = types::contains;

			return Arrays.stream(values()) //
					.filter(isIncluded.negate());
		}

		public static Stream<DependencyType> allBut(Stream<DependencyType> types) {
			return allBut(types.collect(Collectors.toList()));
		}

		/**
		 * Returns all {@link DependencyType}s except the given ones.
		 *
		 * @param types must not be {@literal null}.
		 * @return
		 */
		public static Stream<DependencyType> allBut(DependencyType... types) {

			Assert.notNull(types, "Types must not be null!");

			return allBut(Arrays.asList(types));
		}
	}
}
