/*
 * Copyright 2022-2023 the original author or authors.
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
package example.order;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;

/**
 * @author Oliver Drotbohm
 */
@ApplicationModuleTest
class OrderIntegrationTests {

	private final OrderManagement orders;

	OrderIntegrationTests(OrderManagement orders) {
		this.orders = orders;
	}

	@Test
	void publishesOrderCompletion(AssertablePublishedEvents events) {

		var reference = new Order();

		orders.complete(reference);

		// Verification

		var matchingMapped = events.ofType(OrderCompleted.class)
				.matching(OrderCompleted::orderId, reference.getId());

		assertThat(matchingMapped).hasSize(1);

		// AssertJ

		assertThat(events)
				.contains(OrderCompleted.class)
				.matching(OrderCompleted::orderId, reference.getId());
	}
}
