/*
 * Copyright 2020-2024 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.modulith.PackageInfo;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * @author Oliver Drotbohm
 */
class Types {

	@Nullable
	@SuppressWarnings("unchecked")
	static <T> Class<T> loadIfPresent(String name) {

		ClassLoader loader = Types.class.getClassLoader();

		return ClassUtils.isPresent(name, loader) ? (Class<T>) ClassUtils.resolveClassName(name, loader) : null;
	}

	static class JMoleculesTypes {

		private static final String BASE_PACKAGE = "org.jmolecules";
		private static final String ANNOTATION_PACKAGE = BASE_PACKAGE + ".ddd.annotation";
		private static final String AT_ENTITY = ANNOTATION_PACKAGE + ".Entity";
		private static final String ARCHUNIT_RULES = BASE_PACKAGE + ".archunit.JMoleculesDddRules";
		private static final String MODULE = ANNOTATION_PACKAGE + ".Module";

		private static final boolean PRESENT = ClassUtils.isPresent(AT_ENTITY, JMoleculesTypes.class.getClassLoader());
		private static final boolean MODULE_PRESENT = ClassUtils.isPresent(MODULE, JMoleculesTypes.class.getClassLoader());

		static final String AT_DOMAIN_EVENT_HANDLER = BASE_PACKAGE + ".event.annotation.DomainEventHandler";
		static final String AT_DOMAIN_EVENT = BASE_PACKAGE + ".event.annotation.DomainEvent";
		static final String DOMAIN_EVENT = BASE_PACKAGE + ".event.types.DomainEvent";

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
		@SuppressWarnings("unchecked")
		public static Class<? extends Annotation> getModuleAnnotationTypeIfPresent() {

			try {
				return isModulePresent()
						? (Class<? extends Annotation>) ClassUtils.forName(MODULE, JMoleculesTypes.class.getClassLoader())
						: null;
			} catch (Exception o_O) {
				return null;
			}
		}

		public static boolean areRulesPresent() {
			return ClassUtils.isPresent(ARCHUNIT_RULES, JMoleculesTypes.class.getClassLoader());
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
		static final String AT_CONFIGURATION_PROPERTIES = BASE_PACKAGE + ".boot.context.properties.ConfigurationProperties";

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
