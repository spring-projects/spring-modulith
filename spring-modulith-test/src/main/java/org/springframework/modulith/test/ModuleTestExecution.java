/*
 * Copyright 2018-2025 the original author or authors.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ApplicationModulesFactory;
import org.springframework.modulith.core.JavaPackage;
import org.springframework.modulith.core.PackageName;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * @author Oliver Drotbohm
 * @author Lukas Dohmen
 */
public class ModuleTestExecution implements Iterable<ApplicationModule> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModuleTestExecution.class);
	private static final ApplicationModulesFactory BOOTSTRAP;

	private static Map<Class<?>, Class<?>> MODULITH_TYPES = new HashMap<>();
	private static Map<Key, ModuleTestExecution> EXECUTIONS = new HashMap<>();

	static {

		var factories = SpringFactoriesLoader.loadFactories(ApplicationModulesFactory.class,
				ModuleTestExecution.class.getClassLoader());

		BOOTSTRAP = !factories.isEmpty() ? factories.get(0) : ApplicationModulesFactory.defaultFactory();
	}

	private final Key key;

	private final BootstrapMode bootstrapMode;
	private final ApplicationModule module;
	private final ApplicationModules modules;
	private final List<ApplicationModule> extraIncludes;

	private final Supplier<List<JavaPackage>> basePackages;
	private final Supplier<List<ApplicationModule>> dependencies;

	private ModuleTestExecution(ApplicationModuleTest annotation, ApplicationModules modules, ApplicationModule module) {

		this.key = new Key(module.getBasePackage().getName(), annotation);
		this.modules = modules;
		this.bootstrapMode = annotation.mode();
		this.module = module;

		this.extraIncludes = getExtraModules(annotation, modules).toList();

		this.basePackages = SingletonSupplier.of(() -> {

			var moduleBasePackages = module.getBootstrapBasePackages(modules, bootstrapMode.getDepth());
			var sharedBasePackages = modules.getSharedModules().stream().map(it -> it.getBasePackage());
			var extraPackages = extraIncludes.stream().map(ApplicationModule::getBasePackage);

			var intermediate = Stream.concat(moduleBasePackages, extraPackages);

			return Stream.concat(intermediate, sharedBasePackages).distinct().toList();
		});

		this.dependencies = SingletonSupplier.of(() -> {

			var bootstrapDependencies = module.getBootstrapDependencies(modules,
					bootstrapMode.getDepth());
			return Stream.concat(bootstrapDependencies, extraIncludes.stream()).distinct().toList();
		});

		if (annotation.verifyAutomatically()) {
			verify();
		}
	}

	public static Supplier<ModuleTestExecution> of(Class<?> type) {

		return () -> {

			var annotation = AnnotatedElementUtils.findMergedAnnotation(type, ApplicationModuleTest.class);
			var packageName = type.getPackage().getName();
			var modules = BOOTSTRAP.of(findSpringBootApplicationByClasses(annotation, type));
			var moduleName = annotation.module();

			var module = StringUtils.hasText(moduleName)
					? modules.getModuleByName(moduleName).orElseThrow( //
							() -> new IllegalStateException(String.format("Unable to find module %s!", moduleName)))
					: modules.getModuleForPackage(packageName).orElseThrow( //
							() -> new IllegalStateException(String.format("Package %s is not part of any module!", packageName)));

			return EXECUTIONS.computeIfAbsent(new Key(module.getBasePackage().getName(), annotation),
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

		var result = isLocatedInRootPackageOrContainedInBasePackages(className);

		if (result) {
			LOGGER.trace("Including class {}.", className);
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

	/**
	 * Returns the {@link BootstrapMode} to be used for the executions.
	 *
	 * @return will never be {@literal null}.
	 */
	public BootstrapMode getBootstrapMode() {
		return bootstrapMode;
	}

	/**
	 * Returns the primary {@link ApplicationModule} to bootstrap.
	 *
	 * @return the module will never be {@literal null}.
	 */
	public ApplicationModule getModule() {
		return module;
	}

	/**
	 * Returns all {@link ApplicationModules} of the application.
	 *
	 * @return the modules will never be {@literal null}.
	 */
	public ApplicationModules getModules() {
		return modules;
	}

	/**
	 * Returns all {@link ApplicationModule}s registered as extra includes for the execution.
	 *
	 * @return the extraIncludes will never be {@literal null}.
	 */
	public List<ApplicationModule> getExtraIncludes() {
		return extraIncludes;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ApplicationModule> iterator() {
		return modules.iterator();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ModuleTestExecution that)) {
			return false;
		}

		return Objects.equals(key, that.key);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(key);
	}

	private boolean isLocatedInRootPackageOrContainedInBasePackages(String className) {

		if (modules.withinRootPackages(className)) {
			return true;
		}

		var candidate = PackageName.ofType(className);

		return basePackages.get().stream().map(JavaPackage::getPackageName).anyMatch(it -> it.contains(candidate));
	}

	private static Stream<ApplicationModule> getExtraModules(ApplicationModuleTest annotation,
			ApplicationModules modules) {

		return Arrays.stream(annotation.extraIncludes()) //
				.map(modules::getModuleByName) //
				.flatMap(Optional::stream);
	}

	private static Class<?> findSpringBootApplicationByClasses(ApplicationModuleTest annotation,
			Class<?> testClass) {

		var types = ObjectUtils.addObjectToArray(annotation.classes(), testClass);

		return Arrays.stream(types)
				.<Class<?>> map(ModuleTestExecution::lookupSpringBootApplicationAnnotation)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Couldn't find @SpringBootApplication traversing %s."
						.formatted(Arrays.stream(types).map(Class::getName).collect(Collectors.joining(", ")))));
	}

	private static Class<?> lookupSpringBootApplicationAnnotation(Class<?> clazz) {

		return MODULITH_TYPES.computeIfAbsent(clazz,
				it -> new AnnotatedClassFinder(SpringBootApplication.class).findFromClass(clazz));
	}

	private static record Key(String moduleBasePackage, ApplicationModuleTest annotation) {}
}
