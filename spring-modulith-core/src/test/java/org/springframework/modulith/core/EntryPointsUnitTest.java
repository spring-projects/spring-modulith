/*
 * Copyright 2026 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import com.acme.withatbean.TestEvents;

import example.entrypoint.internal.InternalApplicationListener;
import example.entrypoint.internal.InternalEventListener;
import example.entrypoint.internal.InternalPlainCreator;
import example.entrypoint.internal.InternalSampleListenerImpl;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

/**
 * Unit tests for {@link EntryPoints}.
 *
 * @author Oliver Drotbohm
 */
class EntryPointsUnitTest {

	String withAtBeanPackage = "com.acme.withatbean";
	ApplicationModule withAtBeanModule = TestUtils.getApplicationModule(withAtBeanPackage);

	ApplicationModule entryPointModule = TestUtils.getApplicationModule("example.entrypoint");

	@Test
	void discoversExposedEntryPointsDirectlyCreatingAnEvent() {

		var publications = withAtBeanModule.getEventPublications();
		var entryPoints = withAtBeanModule.getEntryPoints().filter(TestEvents.class);

		assertThat(entryPoints.stream().filter(publications::containsKey).map(Object::toString))
				.containsExactlyInAnyOrder("c.a.w.TestEvents.method()", "c.a.w.TestEvents.constructorCall()");
	}

	@Test
	void ignoresNonPublicMethodsAsEntryPointsEvenIfDeclaringTypeIsExposed() {

		var entryPoints = withAtBeanModule.getEntryPoints().filter(TestEvents.class);

		assertThat(entryPoints.stream().map(Object::toString))
				.doesNotContain("c.a.w.TestEvents.packagePrivateMethod()");
	}

	@Test
	void doesNotWalkCallGraphBeyondModuleBoundary() {

		// Import the wider "com.acme" package so the call from ExternalConnector (in a sibling package) is actually
		// part of the analyzed call graph, then scope the module down to "com.acme.withatbean" again.
		var pkg = JavaPackage.of(TestUtils.getClasses("com.acme"), withAtBeanPackage);
		var module = new ApplicationModule(ApplicationModuleSource.from(pkg, pkg.getLocalName()));

		var publications = module.getEventPublications();
		var entryPoints = module.getEntryPoints().filter(TestEvents.class);

		assertThat(entryPoints.stream().filter(publications::containsKey).map(Object::toString))
				.doesNotContain("c.a.w.TestEvents.viaExternalRoute(…)");
	}

	@Test
	void discoversFrameworkTriggeredEntryPointsEvenIfDeclaringTypeIsNotExposed() {

		assertThat(entryPointModule.isExposed(InternalEventListener.class)).isFalse();
		assertThat(entryPointModule.isExposed(InternalApplicationListener.class)).isFalse();

		var entryPoints = entryPointModule.getEventPublications().keySet().stream()
				.map(Object::toString)
				.toList();

		// ApplicationListener isn't a module type, so InternalApplicationListener.onApplicationEvent(…) is reported as
		// itself rather than being collapsed into the generic, foreign ApplicationListener.onApplicationEvent(…).
		assertThat(entryPoints).contains(
				"e.e.internal.InternalEventListener.on(…)",
				"e.e.internal.InternalApplicationListener.onApplicationEvent(…)");
	}

	@Test
	void prefersExposedInterfaceMethodOverInternalImplementation() {

		assertThat(entryPointModule.isExposed(InternalSampleListenerImpl.class)).isFalse();

		var entryPoints = entryPointModule.getEventPublications().keySet().stream()
				.map(Object::toString)
				.toList();

		assertThat(entryPoints).contains("e.e.SampleListener.handle()");
		assertThat(entryPoints).doesNotContain("e.e.internal.InternalSampleListenerImpl.handle()");
	}

	@Test
	void ignoresInternalMethodsThatAreNeitherExposedNorFrameworkTriggered() {

		assertThat(entryPointModule.isExposed(InternalPlainCreator.class)).isFalse();

		var entryPoints = entryPointModule.getEntryPoints().filter(InternalPlainCreator.class);

		assertThat(entryPoints.stream().map(Object::toString))
				.doesNotContain("e.e.internal.InternalPlainCreator.create()");
	}

	@Test
	void exposesEntryPointsThroughOwnCollectionType() {

		var classes = entryPointModule.getClasses();
		var listenerMethod = classes.getRequiredClass(InternalEventListener.class).getMethod("on", ApplicationEvent.class);
		var plainMethod = classes.getRequiredClass(InternalPlainCreator.class).getMethod("create");

		var entryPoints = entryPointModule.getEntryPoints();

		assertThat(entryPoints.contains(listenerMethod)).isTrue();
		assertThat(entryPoints.contains(plainMethod)).isFalse();

		assertThat(entryPoints.stream().map(Object::toString))
				.contains("e.e.internal.InternalEventListener.on(…)");
	}
}
