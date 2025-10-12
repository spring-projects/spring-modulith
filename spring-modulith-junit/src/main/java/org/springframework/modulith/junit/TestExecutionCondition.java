/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.junit;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.lang.Nullable;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependencies;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.util.ClassUtils;

/**
 * Primary condition implementation that decides whether to run a test based on an {@link ConditionContext}. Simple
 * facade for testability without needing to instantiate more involved JUnit concepts.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 * @see ModulithExecutionCondition
 */
class TestExecutionCondition {

	private static final Logger log = LoggerFactory.getLogger(TestExecutionCondition.class);
	private static final AnnotatedClassFinder SPA_CLASS_FINDER = new AnnotatedClassFinder(SpringBootConfiguration.class);

	private final Map<ApplicationModule, ApplicationModuleDependencies> dependencies = new HashMap<>();

	ConditionEvaluationResult evaluate(ConditionContext context) {

		var changes = context.changes();

		if (changes.hasClasspathResourceChange()) {
			return enabled("Some classpath resource changed, running all tests.");
		}

		if (changes.hasBuildResourceChanges()) {
			return enabled("Build resource changed, running all tests.");
		}

		if (!changes.hasClassChanges()) {
			return enabled("No source file changes detected.");
		}

		var changedClasses = changes.getChangedClasses();

		log.trace("Found following changed classes {}", changedClasses);

		var testClass = context.testClass();

		if (changedClasses.contains(testClass.getName())) {
			return enabled("Change in test class detected, executing test.");
		}

		var mainClass = findMainClass(testClass);

		if (mainClass == null) {// TODO:: Try with @ApplicationModuleTest -> main class
			return enabled("Unable to locate SpringBootApplication Class");
		}

		var modules = ApplicationModules.of(mainClass);
		var packageName = ClassUtils.getPackageName(testClass);

		return modules.getModuleForPackage(packageName).map(it -> {

			if (it.isRootModule()) {
				return enabled("Always executing tests in root modules.");
			}

			var dependencies = this.dependencies.computeIfAbsent(it, m -> m.getAllDependencies(modules));

			for (String changedClass : changedClasses) {

				if (it.contains(changedClass)) {
					return enabled("Changes detected in module %s, executing test.".formatted(it.getIdentifier()));
				}

				var dependency = dependencies.getModuleByType(changedClass);

				if (dependency != null) {
					return enabled(
							"Changes detected in dependent module %s, executing test.".formatted(dependency.getIdentifier()));
				}
			}

			return disabled("Test residing in module %s not affected by changes!".formatted(it.getIdentifier()));

		}).orElseGet(() -> enabled("Test in package %s does not reside in any module!".formatted(packageName)));
	}

	private static ConditionEvaluationResult enabled(String message) {
		return result(ConditionEvaluationResult::enabled, "▶️  " + message);
	}

	private static ConditionEvaluationResult disabled(String message) {
		return result(ConditionEvaluationResult::disabled, "⏸️  ️" + message);
	}

	private static ConditionEvaluationResult result(Function<String, ConditionEvaluationResult> creator, String message) {

		log.info(message);

		return creator.apply(message);
	}

	/**
	 * Finds the canonical main class of the application. Falls back to {@literal null} in case multiple types are found.
	 *
	 * @param testClass must not be {@literal null}.
	 */
	private static @Nullable Class<?> findMainClass(Class<?> testClass) {

		try {
			return SPA_CLASS_FINDER.findFromClass(testClass);
		} catch (IllegalStateException o_O) {
			return null;
		}
	}

	record ConditionContext(Class<?> testClass, Changes changes) {}
}
