/*
 * Copyright 2022-2024 the original author or authors.
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
package example.inventory;

import example.order.OrderCompleted;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * A Spring {@link Service} exposed by the inventory module.
 *
 * @author Oliver Drotbohm
 */
@Service
@RequiredArgsConstructor
class InventoryManagement {

	private static final Logger LOG = LoggerFactory.getLogger(InventoryManagement.class);

	private final ApplicationEventPublisher events;

	@ApplicationModuleListener
	void on(OrderCompleted event) throws InterruptedException {

		var orderId = event.orderId();

		LOG.info("Received order completion for {}.", orderId);

		// Simulate busy work
		Thread.sleep(1000);
		events.publishEvent(new InventoryUpdated(orderId));

		LOG.info("Finished order completion for {}.", orderId);
	}
}
