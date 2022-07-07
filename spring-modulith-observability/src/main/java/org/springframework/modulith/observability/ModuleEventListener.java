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
package org.springframework.modulith.observability;

import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

import org.springframework.modulith.model.Module;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

/**
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
public class ModuleEventListener implements ApplicationListener<ApplicationEvent> {

	private final ModulesRuntime modules;
	private final Supplier<Tracer> tracer;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {

		if (!PayloadApplicationEvent.class.isInstance(event)) {
			return;
		}

		PayloadApplicationEvent<?> foo = (PayloadApplicationEvent<?>) event;
		Object object = foo.getPayload();
		Class<? extends Object> payloadType = object.getClass();

		if (!modules.isApplicationClass(payloadType)) {
			return;
		}

		Module moduleByType = modules.get()
				.getModuleByType(payloadType.getSimpleName())
				.orElse(null);

		if (moduleByType == null) {
			return;
		}

		Span span = tracer.get().currentSpan();

		if (span == null) {
			return;
		}

		span.event("Published " + payloadType.getName());
	}
}
