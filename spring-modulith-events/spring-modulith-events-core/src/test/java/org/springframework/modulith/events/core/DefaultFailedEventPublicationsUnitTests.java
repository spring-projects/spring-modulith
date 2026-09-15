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
package org.springframework.modulith.events.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.events.AbandonPolicy;
import org.springframework.modulith.events.ResubmissionOptions;

/**
 * Unit tests for {@link DefaultFailedEventPublications}.
 *
 * @author Oliver Drotbohm
 * @author Seonwoo Jung
 */
class DefaultFailedEventPublicationsUnitTests {

	EventPublicationRegistry registry = mock(EventPublicationRegistry.class);
	StandardEnvironment environment = new StandardEnvironment();
	DefaultFailedEventPublications failedEventPublications;

	@BeforeEach
	void setUp() {

		var listeners = new TransactionalEventListeners(List.of(), () -> new StandardEnvironment());

		this.failedEventPublications = new DefaultFailedEventPublications(() -> registry, () -> listeners,
				() -> environment);
	}

	@Test // GH-1764
	void applyAbandonPolicyWithoutOverrideDelegatesToRegistry() {

		failedEventPublications.applyAbandonPolicy();

		verify(registry).applyAbandonPolicy(null);
	}

	@Test // GH-1764
	void applyAbandonPolicyWithOverrideDelegatesToRegistry() {

		AbandonPolicy policy = __ -> AbandonPolicy.Decision.ABANDON;

		failedEventPublications.applyAbandonPolicy(policy);

		verify(registry).applyAbandonPolicy(policy);
	}

	@Test // GH-1764
	void applyAbandonPolicyRejectsNullOverride() {
		assertThatIllegalArgumentException().isThrownBy(() -> failedEventPublications.applyAbandonPolicy(null));
	}

	@Test // GH-1764
	void resubmitDelegatesToRegistry() {

		var options = ResubmissionOptions.defaults();

		failedEventPublications.resubmit(options);

		verify(registry).processFailedPublications(eq(options), any());
	}

	@Test // GH-240, GH-251
	void doesNotRepublishEventsOnRestartByDefault() {

		failedEventPublications.afterSingletonsInstantiated();

		verify(registry, never()).findIncompletePublications();
	}

	@Test // GH-240, GH-251
	void triggersRepublicationIfExplicitlyEnabled() {

		var source = new MapPropertySource("test",
				Map.of(DefaultFailedEventPublications.REPUBLISH_ON_RESTART, "true"));
		environment.getPropertySources().addFirst(source);

		failedEventPublications.afterSingletonsInstantiated();

		verify(registry).processIncompletePublications(any(), any(), any());
	}

	@Test // GH-240, GH-251, GH-823
	void triggersRepublicationIfLegacyConfigExplicitlyEnabled() {

		var source = new MapPropertySource("test",
				Map.of(DefaultFailedEventPublications.REPUBLISH_ON_RESTART_LEGACY, "true"));
		environment.getPropertySources().addFirst(source);

		failedEventPublications.afterSingletonsInstantiated();

		verify(registry).processIncompletePublications(any(), any(), any());
	}
}
