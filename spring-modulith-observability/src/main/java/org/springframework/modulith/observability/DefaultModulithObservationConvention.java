package org.springframework.modulith.observability;

import java.lang.reflect.Method;

import io.micrometer.common.KeyValues;
import org.springframework.modulith.observability.ModulithObservations.HighKeys;
import org.springframework.modulith.observability.ModulithObservations.LowKeys;

public class DefaultModulithObservationConvention implements ModulithObservationConvention {

  @Override
  public KeyValues getLowCardinalityKeyValues(ModulithContext context) {
    ObservedModule currentModule = context.getModule();
    if (currentModule != null) {
      return KeyValues.of(LowKeys.MODULE_KEY.withValue(currentModule.getName()));
    }
    return KeyValues.empty();
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
    // TODO: Change this to application name
    return "[" + context.getModule().getDisplayName() + "] " + context.getModule()
        .getDisplayName();
  }
}
