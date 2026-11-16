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

import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.OnionVerificationOptions;
import org.springframework.util.Assert;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * The verification of Onion Architectures (both the classical and the simplified variant) defined via jMolecules'
 * annotations applied to {@link ApplicationModules}. The rules are applied to each module in isolation. The references
 * between modules are only verified for the ones declared as onion by annotating their base package with
 * {@code org.jmolecules.architecture.onion.Onion}: references to them have to respect their exposure, references from
 * them their referrers. Use {@link #withModulesAsOnions()} to treat all modules as if they were declared that way. The
 * given jMolecules {@link OnionVerificationOptions} can be used to customize the verification, but the unit resolver
 * will always be replaced by one detecting the modules as units, and, for the verification within the modules, the
 * scope by the one of the individual modules.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 * @see VerificationOptions#withArchitectureVerifications(TechnicalArchitectureVerifications)
 */
public final class OnionVerifications implements TechnicalArchitectureVerifications {

	private static final String ONION = "org.jmolecules.architecture.onion.Onion";

	private static final OnionVerifications DEFAULTS = new OnionVerifications(
			OnionVerificationOptions.defaults().withDirectApplicationAccess(), false);

	private final OnionVerificationOptions options;
	private final boolean modulesAsOnions;

	private OnionVerifications(OnionVerificationOptions options, boolean modulesAsOnions) {

		this.options = options;
		this.modulesAsOnions = modulesAsOnions;
	}

	/**
	 * Returns the default {@link OnionVerifications}, i.e. the jMolecules defaults with
	 * {@link OnionVerificationOptions#withDirectApplicationAccess() direct application access}. References between
	 * modules are only verified for the ones declared as onion.
	 *
	 * @return will never be {@literal null}.
	 */
	public static OnionVerifications defaults() {
		return DEFAULTS;
	}

	/**
	 * Returns a new {@link OnionVerifications} using the given {@link OnionVerificationOptions} as they are, only
	 * verifying references between modules for the ones declared as onion.
	 *
	 * @param options must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static OnionVerifications of(OnionVerificationOptions options) {

		Assert.notNull(options, "OnionVerificationOptions must not be null!");

		return new OnionVerifications(options, false);
	}

	/**
	 * Returns a new {@link OnionVerifications} treating all modules as if they were declared as onion via
	 * {@code org.jmolecules.architecture.onion.Onion}, i.e. also verifying the references between modules not explicitly
	 * declared that way, according to the exposure and referrers configured in the {@link OnionVerificationOptions}.
	 * Modules not using any onion rings are not restricted.
	 *
	 * @return will never be {@literal null}.
	 */
	public OnionVerifications withModulesAsOnions() {
		return new OnionVerifications(options, true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.JMoleculesArchitectureVerifications#createRule(org.springframework.modulith.core.ApplicationModules)
	 */
	@Override
	public ArchRule createRule(ApplicationModules modules) {

		var allModules = ModuleArchitecturalUnitResolver.of(modules);
		var units = modulesAsOnions ? allModules : ModuleArchitecturalUnitResolver.ofAnnotated(modules, ONION);
		var betweenOptions = options.withUnitResolver(units);

		return CompositeArchRule
				.of(ModuleScopedRules.forEachModule(modules, options.withUnitResolver(allModules),
						OnionVerifications::bothVariants))
				.and(JMoleculesArchitectureRules.ensureOnionClassicalUnitBoundaries(betweenOptions))
				.and(JMoleculesArchitectureRules.ensureOnionSimpleUnitBoundaries(betweenOptions));
	}

	private static ArchRule bothVariants(OnionVerificationOptions options) {

		return CompositeArchRule.of(JMoleculesArchitectureRules.ensureOnionClassical(options))
				.and(JMoleculesArchitectureRules.ensureOnionSimple(options));
	}
}
