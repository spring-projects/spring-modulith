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

import org.jmolecules.archunit.HexagonalVerificationOptions;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesArchitectureRules.VerificationDepth;
import org.springframework.util.Assert;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * The verification of Hexagonal Architectures defined via jMolecules' annotations applied to
 * {@link ApplicationModules}. The rules are applied to each module in isolation. The references between modules are
 * only verified for the ones declared as hexagon by annotating their base package with
 * {@code org.jmolecules.architecture.hexagonal.Hexagon}: references to them have to respect their exposure, references
 * from them their referrers. Use {@link #withModulesAsHexagons()} to treat all modules as if they were declared that
 * way. The given jMolecules {@link HexagonalVerificationOptions} can be used to customize the verification, but the
 * unit resolver will always be replaced by one detecting the modules as units, and, for the verification within the
 * modules, the scope by the one of the individual modules.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 * @see VerificationOptions#withArchitectureVerifications(TechnicalArchitectureVerifications)
 */
public final class HexagonalVerifications implements TechnicalArchitectureVerifications {

	private static final String HEXAGON = "org.jmolecules.architecture.hexagonal.Hexagon";

	private static final HexagonalVerifications DEFAULTS = new HexagonalVerifications(
			HexagonalVerificationOptions.defaults()
					.withDirectApplicationAccess()
					.withDepth(VerificationDepth.LENIENT),
			false);

	private final HexagonalVerificationOptions options;
	private final boolean modulesAsHexagons;

	private HexagonalVerifications(HexagonalVerificationOptions options, boolean modulesAsHexagons) {

		this.options = options;
		this.modulesAsHexagons = modulesAsHexagons;
	}

	/**
	 * Returns the default {@link HexagonalVerifications}, i.e. the jMolecules defaults, but
	 * {@link VerificationDepth#LENIENT} and {@link HexagonalVerificationOptions#withDirectApplicationAccess() direct
	 * application access}, so that the application core (application code and ports) may also refer to the primary ports
	 * and application code of other modules. References between modules are only verified for the ones declared as
	 * hexagon.
	 *
	 * @return will never be {@literal null}.
	 */
	public static HexagonalVerifications defaults() {
		return DEFAULTS;
	}

	/**
	 * Returns a new {@link HexagonalVerifications} using the given {@link HexagonalVerificationOptions} as they are, only
	 * verifying references between modules for the ones declared as hexagon.
	 *
	 * @param options must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static HexagonalVerifications of(HexagonalVerificationOptions options) {

		Assert.notNull(options, "HexagonalVerificationOptions must not be null!");

		return new HexagonalVerifications(options, false);
	}

	/**
	 * Returns a new {@link HexagonalVerifications} treating all modules as if they were declared as hexagon via
	 * {@code org.jmolecules.architecture.hexagonal.Hexagon}, i.e. also verifying the references between modules not
	 * explicitly declared that way, according to the exposure and referrers configured in the
	 * {@link HexagonalVerificationOptions}. Modules not using any hexagonal stereotypes are not restricted.
	 *
	 * @return will never be {@literal null}.
	 */
	public HexagonalVerifications withModulesAsHexagons() {
		return new HexagonalVerifications(options, true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.JMoleculesArchitectureVerifications#createRule(org.springframework.modulith.core.ApplicationModules)
	 */
	@Override
	public ArchRule createRule(ApplicationModules modules) {

		var allModules = ModuleArchitecturalUnitResolver.of(modules);
		var units = modulesAsHexagons ? allModules : ModuleArchitecturalUnitResolver.ofAnnotated(modules, HEXAGON);

		var withinModules = ModuleScopedRules.forEachModule(modules, options.withUnitResolver(allModules),
				JMoleculesArchitectureRules::ensureHexagonal);
		var betweenModules = JMoleculesArchitectureRules.ensureHexagonalUnitBoundaries(options.withUnitResolver(units));

		return CompositeArchRule.of(withinModules).and(betweenModules);
	}
}
