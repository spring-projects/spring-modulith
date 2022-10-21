/*
 * Copyright 2022 the original author or authors.
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

import org.springframework.stereotype.Component;

/**
 * Some inventory-internal application component. As it is located in the very same package, it can be protected from
 * access by other modules by using the default visibility instead of making it public.
 *
 * @author Oliver Drotbohm
 */
@Component
class InventoryInternal {}
