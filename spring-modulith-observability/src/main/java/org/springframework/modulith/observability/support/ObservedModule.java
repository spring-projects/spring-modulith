/*
 * Copyright 2022-2026 the original author or authors.
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

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClass;
import org.springframework.modulith.core.ArchitecturallyEvidentType;

/**
 * Information about observed module.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
public interface ObservedModule {

	/**
	 * Returns the name of the application module.
	 *
	 * @return will never be {@literal null}.
	 * @deprecated since 1.3, use {@link #getIdentifier()} instead.
	 */
	@Deprecated(forRemoval = true)
	String getName();

	/**
	 * Returns the {@link ApplicationModuleIdentifier} of the underlying module.
	 *
	 * @return will never be {@literal null}.
	 */
	ApplicationModuleIdentifier getIdentifier();

	/**
	 * Returns the human-readable name of the module.
	 *
	 * @return will never be {@literal null}.
	 */
	String getDisplayName();

	/**
	 * Returns the name of the actually invoked {@link Method}.
	 *
	 * @param invocation must not be {@literal null}.
	 * @return
	 */
	String getInvokedMethod(MethodInvocation invocation);

	/**
	 * Returns whether the {@link ObservedModule} exposes the given {@link JavaClass}.
	 *
	 * @param type
	 * @return
	 */
	boolean exposes(JavaClass type);

	boolean isObservedModule(ApplicationModule module);

	/**
	 * Returns the {@link ObservedModuleType} for the given type and {@link ApplicationModules}.
	 *
	 * @param type must not be {@literal null}.
	 * @param modules must not be {@literal null}.
	 * @return the {@link ObservedModuleType} for the given type or {@literal null} if the type is not to be observed.
	 */
	@Nullable
	ObservedModuleType getObservedModuleType(Class<?> type, ApplicationModules modules);

	/**
	 * Returns whether the given {@link MethodInvocation} is the invocation of an event listener as opposed to a standard
	 * method invocation on a Spring bean.
	 *
	 * @param invocation must not be {@literal null}.
	 * @since 1.4
	 */
	boolean isEventListenerInvocation(MethodInvocation invocation);
}
