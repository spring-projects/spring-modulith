package org.springframework.modulith.observability;

import java.lang.reflect.Method;

import io.micrometer.common.KeyValues;

import org.springframework.modulith.observability.ModulithObservations.HighKeys;
import org.springframework.modulith.observability.ModulithObservations.LowKeys;

/**
 * Default implementation of {@link ModulithObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @since 1.3
 */
public class DefaultModulithObservationConvention implements ModulithObservationConvention {

	@Override
	public KeyValues getLowCardinalityKeyValues(ModulithContext context) {
		KeyValues keyValues = KeyValues.of(LowKeys.MODULE_KEY.withValue(context.getModule().getIdentifier().toString()));
		if (isEventListener(context)) {
			return keyValues.and(LowKeys.INVOCATION_TYPE.withValue("event-listener"));
		}
		return keyValues;
	}

	private boolean isEventListener(ModulithContext context) {
		try {
			return context.getModule().isEventListenerInvocation(context.getInvocation());
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ModulithContext context) {
		Method method = context.getInvocation().getMethod();
		return KeyValues.of(HighKeys.MODULE_METHOD.withValue(method.getName()));
	}

	@Override
	public String getName() {
		return "module.requests";
	}

	@Override
	public String getContextualName(ModulithContext context) {
		return "[" + context.getApplicationName() + "] " + context.getModule().getDisplayName();
	}
}
