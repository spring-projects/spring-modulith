/*
 * Copyright 2020-2022 the original author or authors.
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
package org.springframework.modulith.docs;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.model.Modules;
import org.springframework.modulith.model.SpringBean;

import com.acme.myproject.Application;
import com.acme.myproject.stereotypes.Stereotypes;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Unit tests for {@link Documenter}.
 *
 * @author Oliver Drotbohm
 */
class DocumenterUnitTests {

	Modules modules = Modules.of(Application.class);

	@Test
	void groupsSpringBeansByArchitecturallyEvidentType() {

		Documenter.CanvasOptions.Groupings result = Documenter.CanvasOptions.defaults()
				.groupingBy(Documenter.CanvasOptions.Grouping.of("Representations", Documenter.CanvasOptions.Grouping.nameMatching(".*Representations")))
				.groupingBy(Documenter.CanvasOptions.Grouping.of("Interface implementations", Documenter.CanvasOptions.Grouping.subtypeOf(Stereotypes.SomeAppInterface.class)))
				.groupBeans(modules.getModuleByName("stereotypes").orElseThrow(RuntimeException::new));

		assertThat(result.keySet())
				.extracting(Documenter.CanvasOptions.Grouping::getName)
				.containsExactlyInAnyOrder("Controllers", "Services", "Repositories", "Event listeners",
						"Configuration properties", "Representations", "Interface implementations", "Others");

		List<SpringBean> impls = result.byGroupName("Interface implementations");

		assertThat(impls).hasSize(1) //
				.extracting(it -> it.getType()) //
				.extracting(JavaClass::getSimpleName) //
				.containsExactly("SomeAppInterfaceImplementation");

		List<SpringBean> listeners = result.byGroupName("Event listeners");

		assertThat(listeners).hasSize(2) //
				.extracting(it -> it.getType()) //
				.extracting(JavaClass::getSimpleName) //
				.containsOnly("SomeEventListener", "SomeTxEventListener");
	}

	@Test
	void playWithOutput() {

		Documenter documenter = new Documenter(modules);

		Documenter.CanvasOptions options = Documenter.CanvasOptions.defaults() //
				.groupingBy(Documenter.CanvasOptions.Grouping.of("Representations", Documenter.CanvasOptions.Grouping.nameMatching(".*Representations")));

		assertThatNoException().isThrownBy(() -> {
			modules.forEach(it -> documenter.toModuleCanvas(it, options));
		});
	}
}
