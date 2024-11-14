package org.springframework.modulith.observability;

import io.micrometer.observation.Observation;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.core.env.Environment;

/**
 * A {@link Observation.Context} for Modulithic applications.
 *
 * @author Marcin Grzejsczak
 * @since 1.3
 */
public class ModulithContext extends Observation.Context {

  private final ObservedModule module;

  private final MethodInvocation invocation;

  private final String applicationName;

  public ModulithContext(ObservedModule module, MethodInvocation invocation, Environment environment) {
    this.module = module;
    this.invocation = invocation;
    this.applicationName = environment.getProperty("spring.application.name");
  }

  public ObservedModule getModule() {
    return module;
  }

  public MethodInvocation getInvocation() {
    return invocation;
  }

  public String getApplicationName() {
    return applicationName;
  }
}
