/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moduliths.test;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.moduliths.model.JavaPackage;
import org.moduliths.model.Module;
import org.moduliths.model.Modules;
import org.moduliths.test.ModuleTest.BootstrapMode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.core.annotation.AnnotatedElementUtils;

import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * @author Oliver Gierke
 */
@Slf4j
@EqualsAndHashCode(of = "key")
public class ModuleTestExecution implements Iterable<Module> {

	private static Map<Class<?>, Class<?>> MODULITH_TYPES = new HashMap<>();
	private static Map<Key, ModuleTestExecution> EXECUTIONS = new HashMap<>();

	private final Key key;

	private final @Getter BootstrapMode bootstrapMode;
	private final @Getter Module module;
	private final @Getter Modules modules;
	private final @Getter List<Module> extraIncludes;

	private final Supplier<List<JavaPackage>> basePackages;
	private final Supplier<List<Module>> dependencies;

	private ModuleTestExecution(ModuleTest annotation, Modules modules, Module module) {

		this.key = Key.of(module.getBasePackage().getName(), annotation);
		this.modules = modules;
		this.bootstrapMode = annotation.mode();
		this.module = module;

		this.extraIncludes = getExtraModules(annotation, modules).collect(Collectors.toList());

		this.basePackages = Suppliers.memoize(() -> {

			Stream<JavaPackage> moduleBasePackages = module.getBasePackages(modules, bootstrapMode.getDepth());
			Stream<JavaPackage> sharedBasePackages = modules.getSharedModules().stream().map(it -> it.getBasePackage());
			Stream<JavaPackage> extraPackages = extraIncludes.stream().map(Module::getBasePackage);

			Stream<JavaPackage> intermediate = Stream.concat(moduleBasePackages, extraPackages);

			return Stream.concat(intermediate, sharedBasePackages).distinct().collect(Collectors.toList());
		});

		this.dependencies = Suppliers.memoize(() -> {

			Stream<Module> bootstrapDependencies = module.getBootstrapDependencies(modules, bootstrapMode.getDepth());
			return Stream.concat(bootstrapDependencies, extraIncludes.stream()).collect(Collectors.toList());
		});

		if (annotation.verifyAutomatically()) {
			verify();
		}
	}

	public static java.util.function.Supplier<ModuleTestExecution> of(Class<?> type) {

		return () -> {

			ModuleTest annotation = AnnotatedElementUtils.findMergedAnnotation(type, ModuleTest.class);
			String packageName = type.getPackage().getName();

			Class<?> modulithType = MODULITH_TYPES.computeIfAbsent(type,
					it -> new AnnotatedClassFinder(SpringBootApplication.class).findFromPackage(packageName));
			Modules modules = Modules.of(modulithType);
			Module module = modules.getModuleForPackage(packageName) //
					.orElseThrow(
							() -> new IllegalStateException(String.format("Package %s is not part of any module!", packageName)));

			return EXECUTIONS.computeIfAbsent(Key.of(module.getBasePackage().getName(), annotation),
					it -> new ModuleTestExecution(annotation, modules, module));
		};
	}

	/**
	 * Returns all base packages the current execution needs to use for component scanning, auto-configuration etc.
	 *
	 * @return
	 */
	public Stream<String> getBasePackages() {
		return basePackages.get().stream().map(JavaPackage::getName);
	}

	public boolean includes(String className) {

		boolean result = modules.withinRootPackages(className) //
				|| basePackages.get().stream().anyMatch(it -> it.contains(className));

		if (result) {
			LOG.debug("Including class {}.", className);
		}

		return !result;
	}

	/**
	 * Returns all module dependencies, based on the current {@link BootstrapMode}.
	 *
	 * @return
	 */
	public List<Module> getDependencies() {
		return dependencies.get();
	}

	/**
	 * Explicitly trigger the module structure verification.
	 */
	public void verify() {
		modules.verify();
	}

	/**
	 * Verifies the setup of the module bootstrapped by this execution.
	 */
	public void verifyModule() {
		module.verifyDependencies(modules);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Module> iterator() {
		return modules.iterator();
	}

	private static Stream<Module> getExtraModules(ModuleTest annotation, Modules modules) {

		return Arrays.stream(annotation.extraIncludes()) //
				.map(modules::getModuleByName) //
				.flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty));
	}

	@Value
	@RequiredArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
	private static class Key {

		String moduleBasePackage;
		ModuleTest annotation;
	}
}
