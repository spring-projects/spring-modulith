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
package fixtures.hexagonal.orders;

import org.jmolecules.architecture.hexagonal.Application;

import fixtures.hexagonal.inventory.StockPrimaryPort;

@Application
public class OrderApplicationService {

	StockPrimaryPort stock; // only allowed if application code may refer to primary ports of other modules
}
