/*
 * Copyright 2018-2023 the original author or authors.
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
package org.springframework.modulith.test;

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
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.model.JavaPackage;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * @author Oliver Drotbohm
 */
@Slf4j
@EqualsAndHashCode(of = "key")
public class ModuleTestExecution implements Iterable<ApplicationModule> {

	private static Map<Class<?>, Class<?>> MODULITH_TYPES = new HashMap<>();
	private static Map<Key, ModuleTestExecution> EXECUTIONS = new HashMap<>();

	private final Key key;

	private final @Getter BootstrapMode bootstrapMode;
	private final @Getter ApplicationModule module;
	private final @Getter ApplicationModules modules;
	private final @Getter List<ApplicationModule> extraIncludes;

	private final Supplier<List<JavaPackage>> basePackages;
	private final Supplier<List<ApplicationModule>> dependencies;

	private ModuleTestExecution(ApplicationModuleTest annotation, ApplicationModules modules, ApplicationModule module) {

		this.key = Key.of(module.getBasePackage().getName(), annotation);
		this.modules = modules;
		this.bootstrapMode = annotation.mode();
		this.module = module;

		this.extraIncludes = getExtraModules(annotation, modules).toList();

		this.basePackages = Suppliers.memoize(() -> {

			Stream<JavaPackage> moduleBasePackages = module.getBootstrapBasePackages(modules, bootstrapMode.getDepth());
			Stream<JavaPackage> sharedBasePackages = modules.getSharedModules().stream().map(it -> it.getBasePackage());
			Stream<JavaPackage> extraPackages = extraIncludes.stream().map(ApplicationModule::getBasePackage);

			Stream<JavaPackage> intermediate = Stream.concat(moduleBasePackages, extraPackages);

			return Stream.concat(intermediate, sharedBasePackages).distinct().toList();
		});

		this.dependencies = Suppliers.memoize(() -> {

			Stream<ApplicationModule> bootstrapDependencies = module.getBootstrapDependencies(modules,
					bootstrapMode.getDepth());
			return Stream.concat(bootstrapDependencies, extraIncludes.stream()).toList();
		});

		if (annotation.verifyAutomatically()) {
			verify();
		}
	}

	public static java.util.function.Supplier<ModuleTestExecution> of(Class<?> type) {

		return () -> {

			ApplicationModuleTest annotation = AnnotatedElementUtils.findMergedAnnotation(type, ApplicationModuleTest.class);
			String packageName = type.getPackage().getName();

			Class<?> modulithType = MODULITH_TYPES.computeIfAbsent(type,
					it -> new AnnotatedClassFinder(SpringBootApplication.class).findFromPackage(packageName));
			ApplicationModules modules = ApplicationModules.of(modulithType);
			ApplicationModule module = modules.getModuleForPackage(packageName) //
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
	 * Returns all module dependencies, based on the current {@link ApplicationModuleTest.BootstrapMode}.
	 *
	 * @return
	 */
	public List<ApplicationModule> getDependencies() {
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
	public Iterator<ApplicationModule> iterator() {
		return modules.iterator();
	}

	private static Stream<ApplicationModule> getExtraModules(ApplicationModuleTest annotation,
			ApplicationModules modules) {

		return Arrays.stream(annotation.extraIncludes()) //
				.map(modules::getModuleByName) //
				.flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty));
	}

	@Value
	@RequiredArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
	private static class Key {

		String moduleBasePackage;
		ApplicationModuleTest annotation;
	}
}
