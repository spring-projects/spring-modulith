/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.modulith.observability.support;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanFilter;

/**
 * {@link SpanFilter} that sets a local service name according to the current module's name.
 *
 * @author Marcin Grzejszczak
 * @since 1.4
 */
public class LocalServiceRenamingSpanFilter implements SpanFilter {

	/*
	 * (non-Javadoc)
	 * @see io.micrometer.tracing.exporter.SpanFilter#map(io.micrometer.tracing.exporter.FinishedSpan)
	 */
	@Override
	public FinishedSpan map(FinishedSpan span) {

		String moduleKey = span.getTags().get(ModulithObservations.LowKeys.MODULE_KEY.asString());

		if (moduleKey != null) {
			span.setLocalServiceName(moduleKey);
		}

		return span;
	}
}
