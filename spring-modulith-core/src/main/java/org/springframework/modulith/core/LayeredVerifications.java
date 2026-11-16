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
import org.jmolecules.archunit.LayeredVerificationOptions;
import org.springframework.util.Assert;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * The verification of Layered Architectures defined via jMolecules' annotations applied to {@link ApplicationModules}.
 * The rules are applied to each module in isolation. The references between modules are only verified for the ones
 * declared as layered by annotating their base package with {@code org.jmolecules.architecture.layered.Layered}:
 * references to them have to respect their exposure, references from them their referrers. Use
 * {@link #withModulesAsLayered()} to treat all modules as if they were declared that way. The given jMolecules
 * {@link LayeredVerificationOptions} can be used to customize the verification, but the unit resolver will always be
 * replaced by one detecting the modules as units, and, for the verification within the modules, the scope by the one of
 * the individual modules.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 * @see VerificationOptions#withArchitectureVerifications(TechnicalArchitectureVerifications)
 */
public final class LayeredVerifications implements TechnicalArchitectureVerifications {

	private static final String LAYERED = "org.jmolecules.architecture.layered.Layered";

	private static final LayeredVerifications DEFAULTS = new LayeredVerifications(
			LayeredVerificationOptions.defaults().withDirectApplicationAccess(), false, false);

	private final LayeredVerificationOptions options;
	private final boolean strict, modulesAsLayered;

	private LayeredVerifications(LayeredVerificationOptions options, boolean strict, boolean modulesAsLayered) {

		this.options = options;
		this.strict = strict;
		this.modulesAsLayered = modulesAsLayered;
	}

	/**
	 * Returns the default {@link LayeredVerifications}, i.e. the jMolecules defaults with
	 * {@link LayeredVerificationOptions#withDirectApplicationAccess() direct application access}, and access to all lower
	 * layers allowed. References between modules are only verified for the ones declared as layered.
	 *
	 * @return will never be {@literal null}.
	 */
	public static LayeredVerifications defaults() {
		return DEFAULTS;
	}

	/**
	 * Returns a new {@link LayeredVerifications} using the given {@link LayeredVerificationOptions} as they are, allowing
	 * access to all lower layers and only verifying references between modules for the ones declared as layered.
	 *
	 * @param options must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static LayeredVerifications of(LayeredVerificationOptions options) {

		Assert.notNull(options, "LayeredVerificationOptions must not be null!");

		return new LayeredVerifications(options, false, false);
	}

	/**
	 * Returns a new {@link LayeredVerifications} only allowing access to the next lower layer.
	 *
	 * @return will never be {@literal null}.
	 */
	public LayeredVerifications withStrictLayering() {
		return new LayeredVerifications(options, true, modulesAsLayered);
	}

	/**
	 * Returns a new {@link LayeredVerifications} treating all modules as if they were declared as layered via
	 * {@code org.jmolecules.architecture.layered.Layered}, i.e. also verifying the references between modules not
	 * explicitly declared that way, according to the exposure and referrers configured in the
	 * {@link LayeredVerificationOptions}. Modules not using any layers are not restricted.
	 *
	 * @return will never be {@literal null}.
	 */
	public LayeredVerifications withModulesAsLayered() {
		return new LayeredVerifications(options, strict, true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.JMoleculesArchitectureVerifications#createRule(org.springframework.modulith.core.ApplicationModules)
	 */
	@Override
	public ArchRule createRule(ApplicationModules modules) {

		var allModules = ModuleArchitecturalUnitResolver.of(modules);
		var units = modulesAsLayered ? allModules : ModuleArchitecturalUnitResolver.ofAnnotated(modules, LAYERED);

		var withinModules = ModuleScopedRules.forEachModule(modules, options.withUnitResolver(allModules),
				this::createRule);
		var betweenModules = JMoleculesArchitectureRules.ensureLayeringUnitBoundaries(options.withUnitResolver(units));

		return CompositeArchRule.of(withinModules).and(betweenModules);
	}

	private ArchRule createRule(LayeredVerificationOptions options) {

		return strict
				? JMoleculesArchitectureRules.ensureLayeringStrict(options)
				: JMoleculesArchitectureRules.ensureLayering(options);
	}
}
