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
package example.inventory;

import example.order.Order;
import example.order.OrderCompleted;
import example.order.internal.OrderInternalA;
import lombok.RequiredArgsConstructor;

import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * A Spring {@link Service} exposed by the inventory module.
 *
 * @author Oliver Drotbohm
 */
@Service
@RequiredArgsConstructor
public class InventoryManagement {

	@ApplicationModuleListener
	void on(OrderCompleted event) {
		OrderInternalA orderInternal = new OrderInternalA();
		new Order();
		// ...
	}
}
