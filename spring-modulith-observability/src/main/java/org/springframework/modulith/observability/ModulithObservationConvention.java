package org.springframework.modulith.observability;

import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;

/**
 * {@link ObservationConvention} for {@link ModulithContext}.
 *
 * @author Marcin Grzejszczak
 * @since 1.4
 */
public interface ModulithObservationConvention extends ObservationConvention<ModulithContext> {

  @Override
  default boolean supportsContext(Context context) {
    return context instanceof ModulithContext;
  }
}
