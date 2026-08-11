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
package example.entrypoint.internal;

import example.entrypoint.SampleEvent;

/**
 * A type internal to the {@code example.entrypoint} module, neither exposed via a named interface nor triggered by
 * any framework infrastructure. Even though {@link #create()} is {@code public}, it must not be considered an entry
 * point, as nothing outside the module can actually reach it.
 *
 * @author Oliver Drotbohm
 */
public class InternalPlainCreator {

	public void create() {
		SampleEvent.of();
	}
}
