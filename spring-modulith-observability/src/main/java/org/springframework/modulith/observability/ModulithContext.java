package org.springframework.modulith.observability;

import io.micrometer.observation.Observation;
import org.aopalliance.intercept.MethodInvocation;

/**
 * A {@link Observation.Context} for Modulithic applications.
 *
 * @author Marcin Grzejsczak
 * @since 1.3
 */
public class ModulithContext extends Observation.Context {

  private final ObservedModule module;

  private final MethodInvocation invocation;

  public ModulithContext(ObservedModule module, MethodInvocation invocation) {
    this.module = module;
    this.invocation = invocation;
  }

  public ObservedModule getModule() {
    return module;
  }

  public MethodInvocation getInvocation() {
    return invocation;
  }
}
