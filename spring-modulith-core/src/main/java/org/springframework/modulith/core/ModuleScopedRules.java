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

import java.util.function.Function;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Helper to apply jMolecules architecture rules to each {@link ApplicationModule} in isolation. Refers to jMolecules
 * types and must thus only be used from code that is only executed if the jMolecules ArchUnit integration is present.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
class ModuleScopedRules {

	/**
	 * Creates an {@link ArchRule} applying the given rule factory to the given options, scoped to each
	 * {@link ApplicationModule} in turn, i.e. only considering the module's own types, excluding the ones of nested
	 * modules.
	 *
	 * @param <O> the jMolecules options type.
	 * @param modules must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @param rule must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static <O extends org.jmolecules.archunit.VerificationOptions<O>> ArchRule forEachModule(
			ApplicationModules modules, O options, Function<O, ArchRule> rule) {

		CompositeArchRule result = null;

		for (var module : modules) {

			var nested = module.getNestedModules(modules);
			var scope = DescribedPredicate.<JavaClass> describe("are part of module " + module.getIdentifier(),
					type -> module.contains(type) && nested.stream().noneMatch(it -> it.contains(type)));

			O scoped = options.withScope(scope);
			ArchRule moduleRule = rule.apply(scoped);

			result = result == null ? CompositeArchRule.of(moduleRule) : result.and(moduleRule);
		}

		return result != null ? result : ArchRuleDefinition.classes()
				.that(DescribedPredicate.alwaysFalse())
				.should(new ArchCondition<JavaClass>("be verified") {

					/*
					 * (non-Javadoc)
					 * @see com.tngtech.archunit.lang.ArchCondition#check(java.lang.Object, com.tngtech.archunit.lang.ConditionEvents)
					 */
					@Override
					public void check(JavaClass item, ConditionEvents events) {}
				})
				.allowEmptyShould(true);
	}
}
