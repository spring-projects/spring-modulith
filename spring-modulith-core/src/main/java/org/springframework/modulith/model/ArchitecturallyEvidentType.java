/*
 * Copyright 2020-2023 the original author or authors.
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
package org.springframework.modulith.model;

import static org.springframework.modulith.model.Types.JavaXTypes.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.modulith.model.Types.JMoleculesTypes;
import org.springframework.modulith.model.Types.SpringDataTypes;
import org.springframework.modulith.model.Types.SpringTypes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * A type that is architecturally relevant, i.e. it fulfills a significant role within the architecture.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ArchitecturallyEvidentType {

	private static Map<Key, ArchitecturallyEvidentType> CACHE = new HashMap<>();

	private final @Getter JavaClass type;

	/**
	 * Creates a new {@link ArchitecturallyEvidentType} for the given {@link JavaType} and {@link Classes} of Spring
	 * components.
	 *
	 * @param type must not be {@literal null}.
	 * @param beanTypes must not be {@literal null}.
	 * @return
	 */
	public static ArchitecturallyEvidentType of(JavaClass type, Classes beanTypes) {

		return CACHE.computeIfAbsent(Key.of(type, beanTypes), it -> {

			List<ArchitecturallyEvidentType> delegates = new ArrayList<>();

			if (JMoleculesTypes.isPresent()) {
				delegates.add(new JMoleculesArchitecturallyEvidentType(type));
			}

			if (SpringDataTypes.isPresent()) {
				delegates.add(new SpringDataAwareArchitecturallyEvidentType(type, beanTypes));
			}

			delegates.add(new SpringAwareArchitecturallyEvidentType(type, beanTypes));

			return DelegatingType.of(type, delegates);
		});
	}

	/**
	 * Returns the abbreviated (i.e. every package fragment reduced to its first character) full name.
	 *
	 * @return will never be {@literal null}.
	 */
	String getAbbreviatedFullName() {
		return FormatableJavaClass.of(getType()).getAbbreviatedFullName();
	}

	/**
	 * Returns whether the type is an entity in the DDD sense.
	 *
	 * @return
	 */
	boolean isEntity() {
		return isJpaEntity().test(getType());
	}

	/**
	 * Returns whether the type is considered an aggregate root in the DDD sense.
	 *
	 * @return
	 */
	public abstract boolean isAggregateRoot();

	/**
	 * Returns whether the type is considered a repository in the DDD sense.
	 *
	 * @return
	 */
	public abstract boolean isRepository();

	public boolean isService() {
		return false;
	}

	public boolean isController() {
		return false;
	}

	public boolean isEventListener() {
		return false;
	}

	public boolean isConfigurationProperties() {
		return false;
	}

	public boolean isInjectable() {
		return isService() || isController() || isEventListener() || isConfigurationProperties();
	}

	public boolean isValueObject() {
		return false;
	}

	/**
	 * Returns other types that are interesting in the context of the current {@link ArchitecturallyEvidentType}. For
	 * example, for an event listener this might be the event types the particular listener is interested in.
	 *
	 * @return
	 */
	public Stream<JavaClass> getReferenceTypes() {
		return Stream.empty();
	}

	public Stream<ReferenceMethod> getReferenceMethods() {
		return Stream.empty();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return type.getFullName();
	}

	private static Stream<JavaClass> distinctByName(Stream<JavaClass> types) {

		Set<String> names = new HashSet<>();

		return types.flatMap(it -> {

			if (names.contains(it.getFullName())) {
				return Stream.empty();
			} else {

				names.add(it.getFullName());

				return Stream.of(it);
			}
		});
	}

	static class SpringAwareArchitecturallyEvidentType extends ArchitecturallyEvidentType {

		/**
		 * Methods (meta-)annotated with @EventListener.
		 */
		private static final Predicate<JavaMethod> IS_ANNOTATED_EVENT_LISTENER = it -> //
		Types.isAnnotatedWith(SpringTypes.AT_EVENT_LISTENER).test(it) //
				|| Types.isAnnotatedWith(SpringTypes.AT_TX_EVENT_LISTENER).test(it);

		/**
		 * {@code ApplicationListener.onApplicationEvent(…)}
		 */
		private static final Predicate<JavaMethod> IS_IMPLEMENTING_EVENT_LISTENER = it -> //
		it.getOwner().isAssignableTo(SpringTypes.APPLICATION_LISTENER) //
				&& it.getName().equals("onApplicationEvent") //
				&& !it.reflect().isSynthetic();

		private static final Predicate<JavaMethod> IS_EVENT_LISTENER = IS_ANNOTATED_EVENT_LISTENER
				.or(IS_IMPLEMENTING_EVENT_LISTENER);

		private final boolean injectable;

		public SpringAwareArchitecturallyEvidentType(JavaClass type, Classes beanTypes) {

			super(type);

			this.injectable = beanTypes.contains(type);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isAggregateRoot()
		 */
		@Override
		public boolean isAggregateRoot() {
			return false;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isRepository()
		 */
		@Override
		public boolean isRepository() {
			return Types.isAnnotatedWith(SpringTypes.AT_REPOSITORY).test(getType());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isService()
		 */
		@Override
		public boolean isService() {
			return Types.isAnnotatedWith(SpringTypes.AT_SERVICE).test(getType());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isController()
		 */
		@Override
		public boolean isController() {
			return Types.isAnnotatedWith(SpringTypes.AT_CONTROLLER).test(getType());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isEventListener()
		 */
		@Override
		public boolean isEventListener() {
			return getType().getMethods().stream().anyMatch(IS_EVENT_LISTENER);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isConfigurationProperties()
		 */
		@Override
		public boolean isConfigurationProperties() {
			return Types.isAnnotatedWith(SpringTypes.AT_CONFIGURATION_PROPERTIES).test(getType());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isInjectable()
		 */
		@Override
		public boolean isInjectable() {
			return injectable || SpringTypes.isComponent().test(getType()) || super.isInjectable();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#getOtherTypeReferences()
		 */
		@Override
		public Stream<JavaClass> getReferenceTypes() {

			if (isEventListener()) {

				return distinctByName(getType().getMethods().stream() //
						.filter(IS_EVENT_LISTENER) //
						.flatMap(it -> it.getRawParameterTypes().stream()))
								.sorted(Comparator.comparing(JavaClass::getSimpleName));
			}

			return super.getReferenceTypes();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#getReferenceMethods()
		 */
		@Override
		public Stream<ReferenceMethod> getReferenceMethods() {

			if (!isEventListener()) {
				return super.getReferenceMethods();
			}

			return getType().getMethods().stream() //
					.filter(IS_EVENT_LISTENER)
					.sorted(Comparator.comparing(JavaMethod::getName)
							.thenComparing(it -> it.getRawParameterTypes().size()))
					.map(ReferenceMethod::new);
		}
	}

	static class SpringDataAwareArchitecturallyEvidentType extends ArchitecturallyEvidentType {

		private final Classes beanTypes;

		SpringDataAwareArchitecturallyEvidentType(JavaClass type, Classes beanTypes) {
			super(type);
			this.beanTypes = beanTypes;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isEntity()
		 */
		@Override
		public boolean isEntity() {

			return super.isEntity() //
					|| getType().isAnnotatedWith("org.springframework.data.mongodb.core.mapping.Document");
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.DefaultArchitectuallyEvidentType#isAggregateRoot(org.springframework.modulith.model.Classes)
		 */
		@Override
		public boolean isAggregateRoot() {
			return isEntity() && beanTypes.that(SpringDataTypes.isSpringDataRepository()).stream() //
					.map(JavaClass::reflect) //
					.map(AbstractRepositoryMetadata::getMetadata) //
					.map(RepositoryMetadata::getDomainType) //
					.anyMatch(it -> getType().isAssignableTo(it));
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isRepository()
		 */
		@Override
		public boolean isRepository() {
			return SpringDataTypes.isSpringDataRepository().test(getType());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isController()
		 */
		@Override
		public boolean isController() {
			return Types.isAnnotatedWith("org.springframework.data.rest.webmvc.BasePathAwareController")
					.test(getType());
		}
	}

	static class JMoleculesArchitecturallyEvidentType extends ArchitecturallyEvidentType {

		private static final Predicate<JavaMethod> IS_ANNOTATED_EVENT_LISTENER = Types
				.isAnnotatedWith(JMoleculesTypes.AT_DOMAIN_EVENT_HANDLER)::test;

		JMoleculesArchitecturallyEvidentType(JavaClass type) {
			super(type);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isEntity()
		 */
		@Override
		public boolean isEntity() {

			JavaClass type = getType();

			return Types.isAnnotatedWith(org.jmolecules.ddd.annotation.Entity.class).test(type) || //
					type.isAssignableTo(org.jmolecules.ddd.types.Entity.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isAggregateRoot()
		 */
		@Override
		public boolean isAggregateRoot() {

			JavaClass type = getType();

			return Types.isAnnotatedWith(org.jmolecules.ddd.annotation.AggregateRoot.class).test(type) //
					|| type.isAssignableTo(org.jmolecules.ddd.types.AggregateRoot.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isRepository()
		 */
		@Override
		public boolean isRepository() {

			JavaClass type = getType();

			return Types.isAnnotatedWith(org.jmolecules.ddd.annotation.Repository.class).test(type)
					|| type.isAssignableTo(org.jmolecules.ddd.types.Repository.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isService()
		 */
		@Override
		public boolean isService() {

			JavaClass type = getType();

			return Types.isAnnotatedWith(org.jmolecules.ddd.annotation.Service.class).test(type);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isEventListener()
		 */
		@Override
		public boolean isEventListener() {
			return getType().getMethods().stream().anyMatch(IS_ANNOTATED_EVENT_LISTENER);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isValueObject()
		 */
		@Override
		public boolean isValueObject() {

			JavaClass type = getType();

			return Types.isAnnotatedWith(org.jmolecules.ddd.annotation.ValueObject.class).test(type)
					|| type.isAssignableTo(org.jmolecules.ddd.types.ValueObject.class);
		}
	}

	static class DelegatingType extends ArchitecturallyEvidentType {

		private final Supplier<Boolean> isAggregateRoot, isRepository, isEntity, isService, isController,
				isEventListener, isConfigurationProperties, isInjectable, isValueObject;

		private final Supplier<Collection<JavaClass>> referenceTypes;
		private final Supplier<Collection<ReferenceMethod>> referenceMethods;

		DelegatingType(JavaClass type, Supplier<Boolean> isAggregateRoot,
				Supplier<Boolean> isRepository, Supplier<Boolean> isEntity, Supplier<Boolean> isService,
				Supplier<Boolean> isController, Supplier<Boolean> isEventListener,
				Supplier<Boolean> isConfigurationProperties,
				Supplier<Boolean> isInjectable,
				Supplier<Boolean> isValueObject,
				Supplier<Collection<JavaClass>> referenceTypes,
				Supplier<Collection<ReferenceMethod>> referenceMethods) {

			super(type);

			this.isAggregateRoot = isAggregateRoot;
			this.isRepository = isRepository;
			this.isEntity = isEntity;
			this.isService = isService;
			this.isController = isController;
			this.isEventListener = isEventListener;
			this.isConfigurationProperties = isConfigurationProperties;
			this.isInjectable = isInjectable;
			this.isValueObject = isValueObject;
			this.referenceTypes = referenceTypes;
			this.referenceMethods = referenceMethods;
		}

		public static DelegatingType of(JavaClass type, List<ArchitecturallyEvidentType> types) {

			var isAggregateRoot = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isAggregateRoot));

			var isRepository = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isRepository));

			var isEntity = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isEntity));

			var isService = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isService));

			var isController = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isController));

			var isEventListener = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isEventListener));

			var isConfigurationProperties = Suppliers
					.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isConfigurationProperties));

			var isInjectable = Suppliers.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isInjectable));

			var isValueObject = Suppliers.memoize(() -> types.stream().anyMatch(ArchitecturallyEvidentType::isValueObject));

			Supplier<Collection<JavaClass>> referenceTypes = Suppliers.memoize(() -> types.stream() //
					.flatMap(ArchitecturallyEvidentType::getReferenceTypes) //
					.toList());

			Supplier<Collection<ReferenceMethod>> referenceMethods = Suppliers.memoize(() -> types.stream() //
					.flatMap(ArchitecturallyEvidentType::getReferenceMethods) //
					.toList());

			return new DelegatingType(type, isAggregateRoot, isRepository, isEntity, isService, isController,
					isEventListener, isConfigurationProperties, isInjectable, isValueObject, referenceTypes, referenceMethods);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isAggregateRoot()
		 */
		// @Override
		@Override
		public boolean isAggregateRoot() {
			return isAggregateRoot.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isRepository()
		 */
		// @Override
		@Override
		public boolean isRepository() {
			return isRepository.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isEntity()
		 */
		@Override
		public boolean isEntity() {
			return isEntity.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isService()
		 */
		@Override
		public boolean isService() {
			return isService.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isController()
		 */
		@Override
		public boolean isController() {
			return isController.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isEventListener()
		 */
		@Override
		public boolean isEventListener() {
			return isEventListener.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isConfigurationProperties()
		 */
		@Override
		public boolean isConfigurationProperties() {
			return isConfigurationProperties.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isInjectable()
		 */
		@Override
		public boolean isInjectable() {
			return isInjectable.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#isValueObject()
		 */
		@Override
		public boolean isValueObject() {
			return isValueObject.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#getOtherTypeReferences()
		 */
		@Override
		public Stream<JavaClass> getReferenceTypes() {
			return distinctByName(referenceTypes.get().stream());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ArchitecturallyEvidentType#getReferenceMethods()
		 */
		@Override
		public Stream<ReferenceMethod> getReferenceMethods() {
			return referenceMethods.get().stream();
		}
	}

	@Value(staticConstructor = "of")
	private static class Key {

		JavaClass type;
		Classes beanTypes;
	}

	@Value
	public final class ReferenceMethod {

		private final JavaMethod method;

		public boolean isAsync() {
			return method.isAnnotatedWith(SpringTypes.AT_ASYNC) || method.isMetaAnnotatedWith(SpringTypes.AT_ASYNC);
		}

		public Optional<String> getTransactionPhase() {

			return Optional.ofNullable(method.getAnnotationOfType(SpringTypes.AT_TX_EVENT_LISTENER))
					.map(it -> it.get("phase"))
					.map(Object::toString);
		}
	}
}
