/*
 * Copyright 2025 the original author or authors.
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

	private final Collection<ArchRule> additionalVerifications;

	/**
	 * Creates a new {@link VerificationOptions}.
	 *
	 * @param additionalVerifications must not be {@literal null}.
	 */
	private VerificationOptions(Collection<ArchRule> additionalVerifications) {

		Assert.notNull(additionalVerifications, "Additional verifications must not be null!");

		this.additionalVerifications = additionalVerifications;
	}

	/**
	 * Creates a new {@link VerificationOptions} including jMolecules verifications if present on the classpath.
	 *
	 * @return will never be {@literal null}.
	 */
	public static VerificationOptions defaults() {
		return new VerificationOptions(JMoleculesTypes.getRules());
	}

	/**
	 * Define the additional verifications to be executed. Disables the ones executed by default.
	 *
	 * @param verifications must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public VerificationOptions withAdditionalVerifications(Collection<ArchRule> verifications) {

		Assert.notNull(verifications, "Verifications must not be null!");

		return new VerificationOptions(verifications);
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
		newVerifications.addAll(verifications);

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
	 * Returns all additional verifications.
	 *
	 * @return will never be {@literal null}.
	 */
	Collection<ArchRule> getAdditionalVerifications() {
		return additionalVerifications;
	}
}
