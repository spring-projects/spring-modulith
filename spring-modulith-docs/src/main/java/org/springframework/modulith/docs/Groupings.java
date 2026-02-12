/*
 * Copyright 2023-2026 the original author or authors.
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

import static org.springframework.modulith.docs.Documenter.CanvasOptions.Grouping.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.function.Predicate;

import org.jmolecules.architecture.cqrs.Command;
import org.jmolecules.architecture.cqrs.CommandDispatcher;
import org.jmolecules.architecture.cqrs.CommandHandler;
import org.jmolecules.architecture.cqrs.QueryModel;
import org.jmolecules.architecture.hexagonal.Adapter;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.hexagonal.Port;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.jmolecules.architecture.layered.DomainLayer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.jmolecules.architecture.onion.classical.ApplicationServiceRing;
import org.jmolecules.architecture.onion.classical.DomainModelRing;
import org.jmolecules.architecture.onion.classical.DomainServiceRing;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.modulith.core.SpringBean;
import org.springframework.modulith.docs.Documenter.CanvasOptions.Grouping;
import org.springframework.util.ClassUtils;

/**
 * A collection of {@link Grouping}s.
 *
 * @author Oliver Drotbohm
 */
public class Groupings {

	/**
	 * Spring Framework-related {@link Grouping}s.
	 *
	 * @author Oliver Drotbohm
	 */
	public static class SpringGroupings {

		/**
		 * Returns Spring Framework-related {@link Grouping}s.
		 *
		 * @return will never be {@literal null}.
		 */
		public static Grouping[] getGroupings() {

			return new Grouping[] {
					of("Controllers", bean -> bean.toArchitecturallyEvidentType().isController()),
					of("Services", bean -> bean.toArchitecturallyEvidentType().isService()),
					of("Repositories", bean -> bean.toArchitecturallyEvidentType().isRepository()),
					of("Event listeners", bean -> bean.toArchitecturallyEvidentType().isEventListener()),
					of("Configuration properties",
							bean -> bean.toArchitecturallyEvidentType().isConfigurationProperties())
			};
		}
	}

	/**
	 * jMolecules-related {@link Grouping}s.
	 *
	 * @author Oliver Drotbohm
	 */
	public static class JMoleculesGroupings {

		private static final boolean JMOLECULES_CQRS_PRESENT = ClassUtils
				.isPresent("org.jmolecules.architecture.cqrs.Command", JMoleculesGroupings.class.getClassLoader());

		private static final boolean JMOLECULES_HEXAGONAL_PRESENT = ClassUtils
				.isPresent("org.jmolecules.architecture.hexagonal.Port", JMoleculesGroupings.class.getClassLoader());

		private static final boolean JMOLECULES_LAYERS_PRESENT = ClassUtils
				.isPresent("org.jmolecules.architecture.layered.ApplicationLayer", JMoleculesGroupings.class.getClassLoader());

		private static final boolean JMOLECULES_ONION_PRESENT = ClassUtils
				.isPresent("org.jmolecules.architecture.onion.simplified.ApplicationRing",
						JMoleculesGroupings.class.getClassLoader());

		public static Grouping[] getGroupings() {

			var groupings = new ArrayList<Grouping>();

			if (JMOLECULES_CQRS_PRESENT) {

				groupings.add(of("Commands", packageOrTypeAnnotatedWith(Command.class)));
				groupings.add(of("Command dispatchers", packageOrTypeAnnotatedWith(CommandDispatcher.class)));
				groupings.add(of("Command handlers", packageOrTypeAnnotatedWith(CommandHandler.class)));
				groupings.add(of("Query models", packageOrTypeAnnotatedWith(QueryModel.class)));
			}

			if (JMOLECULES_HEXAGONAL_PRESENT) {

				groupings.add(of("Primary ports", packageOrTypeAnnotatedWith(PrimaryPort.class)));
				groupings.add(of("Secondary ports", packageOrTypeAnnotatedWith(SecondaryPort.class)));
				groupings.add(of("Ports", packageOrTypeAnnotatedWith(Port.class)));

				groupings.add(of("Application", packageOrTypeAnnotatedWith(Application.class)));

				groupings.add(of("Primary adapters", packageOrTypeAnnotatedWith(PrimaryAdapter.class)));
				groupings.add(of("Secondary adapters", packageOrTypeAnnotatedWith(SecondaryAdapter.class)));
				groupings.add(of("Adapters", packageOrTypeAnnotatedWith(Adapter.class)));
			}

			if (JMOLECULES_LAYERS_PRESENT) {

				groupings.add(of("Application layer", packageOrTypeAnnotatedWith(ApplicationLayer.class)));
				groupings.add(of("Domain layer", packageOrTypeAnnotatedWith(DomainLayer.class)));
				groupings.add(of("Infrastructure layer", packageOrTypeAnnotatedWith(InfrastructureLayer.class)));
				groupings.add(of("Interface layer", packageOrTypeAnnotatedWith(InterfaceLayer.class)));
			}

			if (JMOLECULES_ONION_PRESENT) {

				groupings.add(of("Application ring", packageOrTypeAnnotatedWith(ApplicationRing.class)));
				groupings.add(of("Domain ring", packageOrTypeAnnotatedWith(DomainRing.class)));
				groupings.add(of("Infrastructure ring", packageOrTypeAnnotatedWith(InfrastructureRing.class)));

				groupings.add(of("Application service ring", packageOrTypeAnnotatedWith(ApplicationServiceRing.class)));
				groupings.add(of("Domain service ring", packageOrTypeAnnotatedWith(DomainServiceRing.class)));
				groupings.add(of("Domain model ring", packageOrTypeAnnotatedWith(DomainModelRing.class)));
				groupings.add(of("Infrastructure ring",
						packageOrTypeAnnotatedWith(
								org.jmolecules.architecture.onion.classical.InfrastructureRing.class)));
			}

			return groupings.toArray(Grouping[]::new);
		}
	}

	private static Predicate<SpringBean> packageOrTypeAnnotatedWith(Class<? extends Annotation> annotation) {

		return bean -> {

			var type = bean.getType();
			var pkg = type.getPackage();

			return type.isAnnotatedWith(annotation) || type.isMetaAnnotatedWith(annotation) //
					|| pkg.isAnnotatedWith(annotation) || pkg.isMetaAnnotatedWith(annotation);
		};
	}
}
