/*
 * Copyright 2020-2025 the original author or authors.
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

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static org.springframework.modulith.core.SyntacticSugar.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.jmolecules.ddd.annotation.Module;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.PackageInfo;
import org.springframework.modulith.core.ApplicationModuleSource.ApplicationModuleSourceMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.properties.HasModifiers;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Utility to deal with a variety of types.
 *
 * @author Oliver Drotbohm
 */
public class Types {

	/**
	 * Loads the class with the given name if present on the classpath.
	 *
	 * @param <T> the type to be loaded
	 * @param name the fully-qualified name of the type to be loaded, must not be {@literal null} or empty.
	 * @return can be {@literal null}.
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public static <T> Class<T> loadIfPresent(String name) {

		Assert.hasText(name, "Name must not be null or empty!");

		ClassLoader loader = Types.class.getClassLoader();

		return ClassUtils.isPresent(name, loader) ? (Class<T>) ClassUtils.resolveClassName(name, loader) : null;
	}

	static class JMoleculesTypes {

		private static final String BASE_PACKAGE = "org.jmolecules";
		private static final String ANNOTATION_PACKAGE = BASE_PACKAGE + ".ddd.annotation";
		private static final String AT_ENTITY = ANNOTATION_PACKAGE + ".Entity";
		private static final String MODULE = ANNOTATION_PACKAGE + ".Module";

		private static final String DDD_RULES = BASE_PACKAGE + ".archunit.JMoleculesDddRules";
		private static final String ARCHITECTURE_RULES = BASE_PACKAGE + ".archunit.JMoleculesArchitectureRules";
		private static final String HEXAGONAL = BASE_PACKAGE + ".architecture.hexagonal.Port";
		private static final String LAYERED = BASE_PACKAGE + ".architecture.layered.InfrastructureLayer";
		private static final String ONION = BASE_PACKAGE + ".architecture.onion.classical.InfrastructureRing";

		private static final boolean PRESENT = ClassUtils.isPresent(AT_ENTITY, JMoleculesTypes.class.getClassLoader());
		private static final boolean MODULE_PRESENT = ClassUtils.isPresent(MODULE, JMoleculesTypes.class.getClassLoader());

		static final String AT_DOMAIN_EVENT_HANDLER = BASE_PACKAGE + ".event.annotation.DomainEventHandler";
		static final String AT_DOMAIN_EVENT = BASE_PACKAGE + ".event.annotation.DomainEvent";
		static final String DOMAIN_EVENT = BASE_PACKAGE + ".event.types.DomainEvent";

		private static @Nullable Collection<ArchRule> RULES;

		/**
		 * Returns whether jMolecules is generally present.
		 *
		 * @see #isModulePresent()
		 */
		public static boolean isPresent() {
			return PRESENT;
		}

		/**
		 * Returns whether the jMolecules {@link Module} type is present. We need to guard for this explicitly as the Kotlin
		 * variant of jMolecules DDD does not ship that type.
		 */
		public static boolean isModulePresent() {
			return MODULE_PRESENT;
		}

		@Nullable
		public static Class<? extends Annotation> getModuleAnnotationTypeIfPresent() {
			return isModulePresent() ? Module.class : null;
		}

		/**
		 * Returns an {@link ApplicationModuleSourceMetadata} for the {@link Module} annotation if present.
		 *
		 * @return will never be {@literal null}.
		 */
		@Nullable
		public static ApplicationModuleSourceMetadata getIdentifierSource() {
			return isModulePresent() ? ApplicationModuleSourceMetadata.forAnnotation(Module.class, Module::id) : null;
		}

		/**
		 * Returns all architectural rules to enforce depending on the classpath arrangement.
		 *
		 * @return will never be {@literal null}.
		 */
		public static Collection<ArchRule> getRules() {

			var rules = RULES;

			if (rules == null) {

				var classLoader = JMoleculesTypes.class.getClassLoader();
				rules = new ArrayList<ArchRule>();

				if (ClassUtils.isPresent(DDD_RULES, classLoader)) {
					rules.add(JMoleculesDddRules.all());
				}

				if (!ClassUtils.isPresent(ARCHITECTURE_RULES, classLoader)) {
					return rules;
				}

				if (ClassUtils.isPresent(HEXAGONAL, classLoader)) {
					rules.add(JMoleculesArchitectureRules.ensureHexagonal());
				}

				if (ClassUtils.isPresent(LAYERED, classLoader)) {
					rules.add(JMoleculesArchitectureRules.ensureLayering());
				}

				if (ClassUtils.isPresent(ONION, classLoader)) {
					rules.add(JMoleculesArchitectureRules.ensureOnionClassical());
					rules.add(JMoleculesArchitectureRules.ensureOnionSimple());
				}

				RULES = rules;
			}

			return rules;
		}
	}

	static class JavaTypes {

		static Predicate<JavaClass> IS_CORE_JAVA_TYPE = it -> it.getName().startsWith("java.")
				|| it.getName().startsWith("javax.")
				|| it.isPrimitive();

		static Predicate<JavaClass> IS_NOT_CORE_JAVA_TYPE = Predicate.not(IS_CORE_JAVA_TYPE);

		/**
		 * Returns all related types of the given one, i.e., all public, non-core, non-primitive Java types declared on
		 * public methods (as either return type or parameter) and constructors, with the given filter applied.
		 *
		 * @param type must not be {@literal null}.
		 * @param filter must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @since 2.0
		 */
		static Stream<JavaClass> relatedTypesOf(JavaClass type, Predicate<? super JavaClass> filter) {

			if (JavaTypes.IS_CORE_JAVA_TYPE.or(Predicate.not(JavaTypes::isPublic)).test(type)) {
				return Stream.empty();
			}

			var result = new HashSet<JavaClass>();

			collectExposedTypes(type, result, filter, new HashSet<>());

			return result.stream();
		}

		private static void collectExposedTypes(JavaClass type, Set<JavaClass> exposedTypes,
				Predicate<? super JavaClass> filter, Collection<JavaType> visited) {

			if (visited.contains(type)) {
				return;
			}

			visited.add(type);

			if (filter.test(type)) {
				exposedTypes.add(type);
			}

			var constructorParameters = type.getAllConstructors().stream()
					.filter(JavaTypes::isPublic)
					.flatMap(it -> it.getParameterTypes().stream());

			var methodReturnTypeAndParameters = type.getAllMethods().stream()
					.filter(JavaTypes::isPublic)
					.flatMap(it -> Stream.concat(it.getParameterTypes().stream(), Stream.of(it.getReturnType())));

			Stream.concat(constructorParameters, methodReturnTypeAndParameters)
					.flatMap(it -> it.getAllInvolvedRawTypes().stream())
					.filter(IS_NOT_CORE_JAVA_TYPE.and(JavaTypes::isPublic).and(filter))
					.forEach(it -> collectExposedTypes(it, exposedTypes, filter, visited));
		}

		private static boolean isPublic(HasModifiers source) {
			return source.getModifiers().contains(JavaModifier.PUBLIC);
		}
	}

	static class JavaXTypes {

		private static final String BASE_PACKAGE = "jakarta";

		static final String AT_ENTITY = BASE_PACKAGE + ".persistence.Entity";
		static final String AT_INJECT = BASE_PACKAGE + ".inject.Inject";
		static final String AT_RESOURCE = BASE_PACKAGE + ".annotation.Resource";

		static DescribedPredicate<? super JavaClass> isJpaEntity() {
			return isAnnotatedWith(AT_ENTITY);
		}
	}

	static class SpringTypes {

		private static final String BASE_PACKAGE = "org.springframework";

		static final String APPLICATION_LISTENER = BASE_PACKAGE + ".context.ApplicationListener";
		static final String AT_AUTOWIRED = BASE_PACKAGE + ".beans.factory.annotation.Autowired";
		static final String AT_ASYNC = BASE_PACKAGE + ".scheduling.annotation.Async";
		static final String AT_BEAN = BASE_PACKAGE + ".context.annotation.Bean";
		static final String AT_COMPONENT = BASE_PACKAGE + ".stereotype.Component";
		static final String AT_CONFIGURATION = BASE_PACKAGE + ".context.annotation.Configuration";
		static final String AT_CONTROLLER = BASE_PACKAGE + ".stereotype.Controller";
		static final String AT_EVENT_LISTENER = BASE_PACKAGE + ".context.event.EventListener";
		static final String AT_REPOSITORY = BASE_PACKAGE + ".stereotype.Repository";
		static final String AT_SERVICE = BASE_PACKAGE + ".stereotype.Service";
		static final String AT_SPRING_BOOT_APPLICATION = BASE_PACKAGE + ".boot.autoconfigure.SpringBootApplication";
		static final String AT_TX_EVENT_LISTENER = BASE_PACKAGE + ".transaction.event.TransactionalEventListener";
		static final String AT_CONFIGURATION_PROPERTIES = BASE_PACKAGE
				+ ".boot.context.properties.ConfigurationProperties";

		static DescribedPredicate<? super JavaClass> isConfiguration() {
			return isAnnotatedWith(AT_CONFIGURATION);
		}

		static DescribedPredicate<? super JavaClass> isComponent() {
			return isAnnotatedWith(AT_COMPONENT);
		}

		static DescribedPredicate<? super JavaClass> isConfigurationProperties() {
			return isAnnotatedWith(AT_CONFIGURATION_PROPERTIES);
		}

		static DescribedPredicate<? super JavaClass> isJsr303Validator() {
			return implement("jakarta.validation.ConstraintValidator");
		}

		static boolean isAtBeanMethod(JavaMethod method) {
			return isAnnotatedWith(SpringTypes.AT_BEAN).test(method);
		}
	}

	static class SpringDataTypes {

		private static final String BASE_PACKAGE = SpringTypes.BASE_PACKAGE + ".data";

		static final String REPOSITORY = BASE_PACKAGE + ".repository.Repository";
		static final String AT_REPOSITORY_DEFINITION = BASE_PACKAGE + ".repository.RepositoryDefinition";

		static boolean isPresent() {
			return ClassUtils.isPresent(REPOSITORY, SpringDataTypes.class.getClassLoader());
		}

		static DescribedPredicate<JavaClass> isSpringDataRepository() {
			return is(assignableTo(SpringDataTypes.REPOSITORY)) //
					.or(isAnnotatedWith(SpringDataTypes.AT_REPOSITORY_DEFINITION));
		}
	}

	/**
	 * Creates a new {@link DescribedPredicate} to match classes
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.2
	 */
	static DescribedPredicate<JavaClass> residesInPackageAnnotatedWith(Class<? extends Annotation> type) {

		Assert.notNull(type, "Annotation type must not be null!");

		return new DescribedPredicate<JavaClass>("resides in a package annotated with", type) {

			@Override
			public boolean test(JavaClass t) {

				var pkg = t.getPackage();

				return pkg.isMetaAnnotatedWith(type)
						|| pkg.getClasses().stream()
								.anyMatch(it -> it.isMetaAnnotatedWith(PackageInfo.class) && it.isMetaAnnotatedWith(type));
			}
		};
	}
}
