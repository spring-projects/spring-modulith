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
import example.entrypoint.SampleListener;

/**
 * An internal, non-exposed implementation of the module's exposed {@link SampleListener} contract. Even though this
 * type itself isn't exposed, {@link #handle()} is still a legitimate entry point, as external code depends on and
 * invokes it through the exposed {@link SampleListener} interface.
 *
 * @author Oliver Drotbohm
 */
public class InternalSampleListenerImpl implements SampleListener {

	@Override
	public void handle() {
		SampleEvent.of();
	}
}
