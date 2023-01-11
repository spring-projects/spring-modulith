/*
 * Copyright 2022-2023 the original author or authors.
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
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.aop.TargetClassAware;
import org.springframework.aop.framework.Advised;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.model.ArchitecturallyEvidentType;
import org.springframework.modulith.model.ArchitecturallyEvidentType.ReferenceMethod;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Represents a type in an {@link ObservedModule}.
 *
 * @author Oliver Drotbohm
 */
public class ObservedModuleType {

	private static Collection<Class<?>> IGNORED_TYPES = List.of(Advised.class, TargetClassAware.class);

	private final ApplicationModules modules;
	private final ObservedModule module;
	private final ArchitecturallyEvidentType type;

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
	}

	/**
	 * Returns whether the type should be traced at all. Can be skipped for types not exposed by the module unless they
	 * listen to events of other modules.
	 *
	 * @return
	 */
	public boolean shouldBeTraced() {

		boolean isApiType = module.exposes(type.getType());

		return type.isController() || listensToOtherModulesEvents() || isApiType;
	}

	/**
	 * Returns a predicate to filter the methods to intercept. For event listeners it's the listener methods only. For
	 * everything else, all (public) methods will be intercepted.
	 *
	 * @return
	 */
	public Predicate<Method> getMethodsToIntercept() {

		if (!type.isEventListener()) {
			return it -> !(ReflectionUtils.isObjectMethod(it) || IGNORED_TYPES.contains(it.getDeclaringClass()));
		}

		return candidate -> type.getReferenceMethods() //
				.map(ReferenceMethod::getMethod) //
				.anyMatch(it -> it.reflect().equals(candidate));
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
