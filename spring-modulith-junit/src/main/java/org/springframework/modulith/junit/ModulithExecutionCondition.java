/*
 * Copyright 2024 the original author or authors.
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

import java.lang.StackWalker.StackFrame;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.lang.Nullable;
import org.springframework.modulith.junit.TestExecutionCondition.ConditionContext;

/**
 * JUnit Extension to optimize test execution based on which files have changed in a Spring Modulith project.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
public class ModulithExecutionCondition implements ExecutionCondition {

	private static final Collection<String> IDE_PACKAGES = Set.of("org.eclipse.jdt", "com.intellij");
	private static final @Nullable TestExecutionCondition CONDITION;

	static {

		var walker = StackWalker.getInstance();

		CONDITION = walker.walk(ModulithExecutionCondition::hasIdeRoot) ? null : new TestExecutionCondition();
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.ExecutionCondition#evaluateExecutionCondition(org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {

		if (CONDITION == null) {
			return ConditionEvaluationResult.enabled("Optimization disabled in IDE execution!");
		}

		if (context.getTestMethod().isPresent()) {
			return ConditionEvaluationResult.enabled("Not filtering on a per method basis!");
		}

		return context.getTestClass().map(testClass -> {

			var store = new StateStore(context);
			var changes = store.getChanges();

			return changes.isEmpty()
					? ConditionEvaluationResult.enabled("No changes detected!")
					: CONDITION.evaluate(new ConditionContext(testClass, changes));

		}).orElseGet(() -> ConditionEvaluationResult.enabled("Not a test class context!"));
	}

	/**
	 * Returns whether the class is loaded in an IDE context.
	 */
	private static boolean hasIdeRoot(Stream<StackFrame> frames) {

		var all = frames.toList();
		var rootClassName = all.get(all.size() - 1).getClassName();

		return IDE_PACKAGES.stream().anyMatch(rootClassName::startsWith);
	}
}
