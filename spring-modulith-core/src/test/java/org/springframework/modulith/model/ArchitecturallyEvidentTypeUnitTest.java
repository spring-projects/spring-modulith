/*
 * Copyright 2020 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.jmolecules.event.annotation.DomainEventHandler;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.data.repository.CrudRepository;
import org.springframework.modulith.model.ArchitecturallyEvidentType.SpringAwareArchitecturallyEvidentType;
import org.springframework.modulith.model.ArchitecturallyEvidentType.SpringDataAwareArchitecturallyEvidentType;
import org.springframework.stereotype.Repository;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Unit tests for {@link ArchitecturallyEvidentType}.
 *
 * @author Oliver Drotbohm
 */
class ArchitecturallyEvidentTypeUnitTest {

	Classes classes = TestUtils.getClasses();
	JavaClass self = classes.getRequiredClass(ArchitecturallyEvidentTypeUnitTest.class);

	@Test
	void abbreviatesFullyQualifiedTypeName() {

		ArchitecturallyEvidentType type = ArchitecturallyEvidentType.of(self, classes);

		assertThat(type.getAbbreviatedFullName()).isEqualTo("o.s.m.m.ArchitecturallyEvidentTypeUnitTest");
	}

	@Test
	void doesNotConsiderArbitraryTypeAStereotype() {

		ArchitecturallyEvidentType type = ArchitecturallyEvidentType.of(self, classes);

		assertThat(type.isEntity()).isFalse();
		assertThat(type.isAggregateRoot()).isFalse();
		assertThat(type.isRepository()).isFalse();
	}

	@Test
	void detectsSpringAnnotatedRepositories() {

		ArchitecturallyEvidentType type = new SpringAwareArchitecturallyEvidentType(
				classes.getRequiredClass(SpringRepository.class));

		assertThat(type.isRepository()).isTrue();
	}

	@Test
	void doesNotConsiderEntityAggregateRoot() {

		ArchitecturallyEvidentType type = new SpringAwareArchitecturallyEvidentType(
				classes.getRequiredClass(SampleEntity.class));

		assertThat(type.isEntity()).isTrue();
		assertThat(type.isAggregateRoot()).isFalse();
	}

	@Test
	void considersEntityAnAggregateRootIfTheresARepositoryForIt() {

		Map<Class<?>, Boolean> parameters = new HashMap<Class<?>, Boolean>();
		parameters.put(SampleEntity.class, true);
		parameters.put(OtherEntity.class, false);
		parameters.put(NoEntity.class, false);

		parameters.entrySet().stream().forEach(it -> {

			JavaClass entity = classes.getRequiredClass(it.getKey());

			assertThat(new SpringDataAwareArchitecturallyEvidentType(entity, classes).isAggregateRoot())
					.isEqualTo(it.getValue());
		});
	}

	@TestFactory
	Stream<DynamicTest> considersJMoleculesEntity() {

		return DynamicTest.stream(getTypesFor(JMoleculesAnnotatedEntity.class, JMoleculesImplementingEntity.class), //
				it -> String.format("%s is considered an entity", it.getType().getSimpleName()), //
				it -> {
					assertThat(it.isEntity()).isTrue();
					assertThat(it.isAggregateRoot()).isFalse();
					assertThat(it.isRepository()).isFalse();
				});
	}

	@TestFactory
	Stream<DynamicTest> considersJMoleculesAggregateRoot() {

		return DynamicTest.stream(
				getTypesFor(JMoleculesAnnotatedAggregateRoot.class, JMoleculesImplementingAggregateRoot.class), //
				it -> String.format("%s is considered an entity, aggregate root but not a repository",
						it.getType().getSimpleName()), //
				it -> {
					assertThat(it.isEntity()).isTrue();
					assertThat(it.isAggregateRoot()).isTrue();
					assertThat(it.isRepository()).isFalse();
				});
	}

	@TestFactory
	Stream<DynamicTest> considersJMoleculesRepository() {

		return DynamicTest.stream(getTypesFor(JMoleculesAnnotatedRepository.class), //
				it -> String.format("%s is considered a repository", it.getType().getSimpleName()), //
				it -> {
					assertThat(it.isEntity()).isFalse();
					assertThat(it.isAggregateRoot()).isFalse();
					assertThat(it.isRepository()).isTrue();
				});
	}

	@Test
	void discoversEventsListenedToForEventListener() {

		JavaClass listenerType = classes.getRequiredClass(SomeEventListener.class);

		assertThat(ArchitecturallyEvidentType.of(listenerType, classes).getReferenceTypes()) //
				.extracting(JavaClass::getFullName) //
				.containsExactly(Object.class.getName(), String.class.getName());
	}

	@Test
	void discoversImplementingEventListener() {

		JavaClass listenerType = classes.getRequiredClass(ImplementingEventListener.class);

		assertThat(ArchitecturallyEvidentType.of(listenerType, classes).getReferenceTypes()) //
				.extracting(JavaClass::getFullName) //
				.containsExactly(ApplicationReadyEvent.class.getName());
	}

	@Test
	void discoversJMoleculesEventHandler() {

		JavaClass type = classes.getRequiredClass(JMoleculesEventListener.class);

		assertThat(ArchitecturallyEvidentType.of(type, classes).isEventListener()).isTrue();
	}

	@Test
	void discoversJMoleculesRepository() {

		JavaClass type = classes.getRequiredClass(JMoleculesImplementingRepository.class);

		assertThat(ArchitecturallyEvidentType.of(type, classes).isRepository()).isTrue();
	}

	private Iterator<ArchitecturallyEvidentType> getTypesFor(Class<?>... types) {

		return Stream.of(types) //
				.map(classes::getRequiredClass) //
				.map(it -> ArchitecturallyEvidentType.of(it, classes)) //
				.iterator();
	}

	// Spring

	@Repository
	interface SpringRepository {}

	@Entity
	class SampleEntity {}

	// Spring Data

	interface SampleRepository extends CrudRepository<SampleEntity, UUID> {}

	@Entity
	class OtherEntity {}

	class NoEntity {}

	// jMolecules

	@org.jmolecules.ddd.annotation.Entity
	class JMoleculesAnnotatedEntity {}

	@org.jmolecules.ddd.annotation.AggregateRoot
	class JMoleculesAnnotatedAggregateRoot {}

	class JMoleculesImplementingIdentifier implements org.jmolecules.ddd.types.Identifier {}

	abstract class JMoleculesImplementingEntity
			implements
			org.jmolecules.ddd.types.Entity<JMoleculesImplementingAggregateRoot, JMoleculesImplementingIdentifier> {}

	abstract class JMoleculesImplementingAggregateRoot
			implements
			org.jmolecules.ddd.types.AggregateRoot<JMoleculesImplementingAggregateRoot, JMoleculesImplementingIdentifier> {}

	@org.jmolecules.ddd.annotation.Repository
	class JMoleculesAnnotatedRepository {}

	interface JMoleculesEventListener {

		@DomainEventHandler
		void on(Object event);
	}

	interface JMoleculesImplementingRepository extends
			org.jmolecules.ddd.types.Repository<JMoleculesImplementingAggregateRoot, JMoleculesImplementingIdentifier> {}

	// Spring

	class SomeEventListener {

		@EventListener
		void on(Object event) {}

		@EventListener
		void on(String event) {}

		@EventListener
		void onOther(Object event) {}
	}

	class ImplementingEventListener implements ApplicationListener<ApplicationReadyEvent> {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
		 */
		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {}
	}
}
