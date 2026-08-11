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

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;

/**
 * A type internal to the {@code example.entrypoint} module (not exposed via any named interface) that's still a
 * legitimate entry point, as its listener method is invoked directly by the framework.
 *
 * @author Oliver Drotbohm
 */
public class InternalEventListener {

	@EventListener
	public void on(ApplicationEvent event) {
		SampleEvent.of();
	}
}
