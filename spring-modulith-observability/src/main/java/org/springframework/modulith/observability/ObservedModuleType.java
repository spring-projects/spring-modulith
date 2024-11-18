/*
 * Copyright 2022-2024 the original author or authors.
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.aop.TargetClassAware;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ArchitecturallyEvidentType;
import org.springframework.modulith.core.ArchitecturallyEvidentType.ReferenceMethod;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Represents a type in an {@link ObservedModule}.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
public class ObservedModuleType {

	private static Collection<Class<?>> IGNORED_TYPES = List.of(Advised.class, TargetClassAware.class);
	private static Predicate<Method> IS_USER_METHOD = it -> !Modifier.isPrivate(it.getModifiers())
			&& !(ReflectionUtils.isObjectMethod(it) || IGNORED_TYPES.contains(it.getDeclaringClass()));

	private final ApplicationModules modules;
	private final ObservedModule module;
	private final ArchitecturallyEvidentType type;
	private final Predicate<Method> methodsToInterceptFilter;

	/**
	 * Creates a new {@link ObservedModuleType} for the given {@link ApplicationModules}, {@link ObservedModule} and
	 * {@link ArchitecturallyEvidentType}.
	 *
	 * @param modules must not be {@literal null}.
	 * @param module must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 */
	ObservedModuleType(ApplicationModules modules, ObservedModule module, ArchitecturallyEvidentType type) {

		Assert.notNull(modules, "ApplicationModules must not be null!");
		Assert.notNull(module, "ObservedModule must not be null!");
		Assert.notNull(type, "ArchitecturallyEvidentType must not be null!");

		this.modules = modules;
		this.module = module;
		this.type = type;

		Predicate<Method> isReferenceMethod = candidate -> type.isEventListener() && type.getReferenceMethods() //
				.map(ReferenceMethod::getMethod) //
				.anyMatch(it -> it.reflect().equals(candidate));

		this.methodsToInterceptFilter = IS_USER_METHOD.or(isReferenceMethod);
	}

	/**
	 * Returns whether the type should be observed at all. Can be skipped for types not exposed by the module unless they
	 * listen to events of other modules.
	 *
	 * @return
	 */
	public boolean shouldBeObserved() {

		if (type.getType().isMetaAnnotatedWith(Configuration.class)) {
			return false;
		}

		return type.isController()
				|| listensToOtherModulesEvents()
				|| module.exposes(type.getType());
	}

	/**
	 * Returns a predicate to filter the methods to intercept. All user declared methods are intercepted, except from
	 * well-known interfaces ({@code Advised}, {@code TargetClassAware}). For event listeners, package-protected methods
	 * are supported as well.
	 *
	 * @return will never be {@literal null}.
	 */
	public Predicate<Method> getMethodsToIntercept() {
		return methodsToInterceptFilter;
	}

	private boolean listensToOtherModulesEvents() {

		if (!type.isEventListener()) {
			return false;
		}

		return type.getReferenceTypes()
				.flatMap(it -> modules
						.getModuleByType(it)
						.map(Stream::of)
						.orElseGet(Stream::empty))
				.findFirst()
				.map(it -> !module.isObservedModule(it))
				.orElse(true);
	}

}
