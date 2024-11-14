package org.springframework.modulith.observability;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanFilter;

/**
 * {@link SpanFilter} that sets a local service name according
 * to the current module's name.
 *
 * @author Marcin Grzejszczak
 * @since 1.3
 */
public class LocalServiceRenamingSpanFilter implements SpanFilter {

	@Override
	public FinishedSpan map(FinishedSpan span) {
		String moduleKey = span.getTags().get(ModulithObservations.LowKeys.MODULE_KEY.asString());
		if (moduleKey != null) {
			// Wait for tracing 1.5.0
			span.setLocalServiceName(moduleKey);
		}
		return span;
	}
}
