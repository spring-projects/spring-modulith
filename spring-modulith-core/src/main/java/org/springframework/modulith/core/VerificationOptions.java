/*
 * Copyright 2025-2026 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.util.Assert;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Options to customize application module verifications.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 * @see #defaults()
 */
public class VerificationOptions {

	private final List<Verification> additionalVerifications;

	/**
	 * Creates a new {@link VerificationOptions}.
	 *
	 * @param additionalVerifications must not be {@literal null}.
	 */
	private VerificationOptions(List<Verification> additionalVerifications) {

		Assert.notNull(additionalVerifications, "Additional verifications must not be null!");

		this.additionalVerifications = additionalVerifications;
	}

	/**
	 * Creates a new {@link VerificationOptions} including jMolecules verifications if present on the classpath. The
	 * architecture verifications (Hexagonal, Onion and Layered Architecture) are applied to each
	 * {@link ApplicationModule} in isolation, as what modules may refer to in each other is governed by the module
	 * verification. Only the references to and from modules explicitly declared as hexagon, onion or layered via
	 * jMolecules' unit annotations are verified, too. Use {@link #withArchitectureVerifications(JMoleculesArchitectureVerifications)}
	 * to customize them.
	 *
	 * @return will never be {@literal null}.
	 */
	public static VerificationOptions defaults() {
		return new VerificationOptions(JMoleculesTypes.getVerifications());
	}

	/**
	 * Registers the given jMolecules architecture verifications, replacing the default ones for the same architectural
	 * style, for example with a customized {@link HexagonalVerifications} instance.
	 *
	 * @param verifications must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 2.2
	 * @see HexagonalVerifications
	 * @see OnionVerifications
	 * @see LayeredVerifications
	 */
	public VerificationOptions withArchitectureVerifications(TechnicalArchitectureVerifications verifications) {

		Assert.notNull(verifications, "Architecture verifications must not be null!");

		var verification = Verification.of(verifications);
		var newVerifications = new ArrayList<Verification>();
		var replaced = false;

		for (var candidate : additionalVerifications) {

			if (candidate.isOfSameKindAs(verification)) {
				newVerifications.add(verification);
				replaced = true;
			} else {
				newVerifications.add(candidate);
			}
		}

		if (!replaced) {
			newVerifications.add(verification);
		}

		return new VerificationOptions(newVerifications);
	}

	/**
	 * Define the additional verifications to be executed. Disables the ones executed by default.
	 *
	 * @param verifications must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public VerificationOptions withAdditionalVerifications(Collection<ArchRule> verifications) {

		Assert.notNull(verifications, "Verifications must not be null!");

		return new VerificationOptions(verifications.stream().map(Verification::of).toList());
	}

	/**
	 * Define the additional verifications to be executed. Disables the ones executed by default.
	 *
	 * @param verifications must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public VerificationOptions withAdditionalVerifications(ArchRule... verifications) {
		return withAdditionalVerifications(List.of(verifications));
	}

	/**
	 * Registers additional verifications on top of the default ones.
	 *
	 * @param verifications must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public VerificationOptions andAdditionalVerifications(Collection<ArchRule> verifications) {

		Assert.notNull(verifications, "Verifications must not be null!");

		var newVerifications = new ArrayList<>(additionalVerifications);
		verifications.stream().map(Verification::of).forEach(newVerifications::add);

		return new VerificationOptions(newVerifications);
	}

	/**
	 * Registers additional verifications on top of the default ones.
	 *
	 * @param verifications must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public VerificationOptions andAdditionalVerifications(ArchRule... verifications) {
		return andAdditionalVerifications(List.of(verifications));
	}

	/**
	 * Disables the additional verifications registered by default.
	 *
	 * @return will never be {@literal null}.
	 */
	public VerificationOptions withoutAdditionalVerifications() {
		return new VerificationOptions(Collections.emptyList());
	}

	/**
	 * Returns all additional verifications created for the given {@link ApplicationModules}.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Collection<ArchRule> getAdditionalVerifications(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		return additionalVerifications.stream()
				.map(it -> it.factory().apply(modules))
				.toList();
	}

	/**
	 * A verification to be executed, created for the {@link ApplicationModules} to be verified. Architecture
	 * verifications carry their type as kind, so that customized ones can replace the defaults.
	 *
	 * @param kind can be {@literal null}.
	 * @param factory must not be {@literal null}.
	 */
	record Verification(@Nullable Class<?> kind, Function<ApplicationModules, ArchRule> factory) {

		static Verification of(ArchRule rule) {
			return new Verification(null, __ -> rule);
		}

		static Verification of(TechnicalArchitectureVerifications verifications) {
			return new Verification(verifications.getClass(), verifications::createRule);
		}

		boolean isOfSameKindAs(Verification other) {
			return kind != null && kind.equals(other.kind);
		}
	}
}
