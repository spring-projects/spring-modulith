package org.springframework.modulith.junit;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.util.ClassUtils;

import com.tngtech.archunit.core.domain.JavaClass;

// add logging to explain what happens (and why)

/**
 * Junit Extension to skip test execution if no changes happened in the module that the test belongs to.
 *
 * @author Lukas Dohmen, David Bilge
 */
public class ModulithExecutionExtension implements ExecutionCondition {
	public static final String CONFIG_PROPERTY_PREFIX = "spring.modulith.test";
	final AnnotatedClassFinder spaClassFinder = new AnnotatedClassFinder(SpringBootApplication.class);
	private static final Logger log = LoggerFactory.getLogger(ModulithExecutionExtension.class);

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		if (context.getTestMethod().isPresent()) {
			// Is there something similar liken @TestInstance(TestInstance.Lifecycle.PER_CLASS) for Extensions?
			return ConditionEvaluationResult.enabled("Enabled, only evaluating per class");
		}

		StateStore stateStore = new StateStore(context);
		Set<Class<?>> changedClasses = stateStore.getChangedClasses();
		if (changedClasses.isEmpty()) {
			log.trace("No class changes found, running tests");
			return ConditionEvaluationResult.enabled("ModulithExecutionExtension: No changes detected");
		}

		log.trace("Found following changed classes {}", changedClasses);

		Optional<Class<?>> testClass = context.getTestClass();
		if (testClass.isPresent()) {
			if (changedClasses.contains(testClass.get())) {
				return ConditionEvaluationResult.enabled("ModulithExecutionExtension: Change in test class detected");
			}
			Class<?> mainClass = this.spaClassFinder.findFromClass(testClass.get());

			if (mainClass == null) {// TODO:: Try with @ApplicationModuleTest -> main class
				return ConditionEvaluationResult.enabled(
						"ModulithExecutionExtension: Unable to locate SpringBootApplication Class");
			}
			ApplicationModules applicationModules = ApplicationModules.of(mainClass);

			String packageName = ClassUtils.getPackageName(testClass.get());

			// always run test if one of whitelisted files is modified (ant matching)
			Optional<ApplicationModule> optionalApplicationModule = applicationModules.getModuleForPackage(packageName);
			if (optionalApplicationModule.isPresent()) {

				Set<JavaClass> dependentClasses = getAllDependentClasses(optionalApplicationModule.get(),
						applicationModules);

				for (Class<?> changedClass : changedClasses) {

					if (optionalApplicationModule.get().contains(changedClass)) {
						return ConditionEvaluationResult.enabled(
								"ModulithExecutionExtension: Changes in module detected, Executing tests");
					}

					if (dependentClasses.stream()
							.anyMatch(applicationModule -> applicationModule.isEquivalentTo(changedClass))) {
						return ConditionEvaluationResult.enabled(
								"ModulithExecutionExtension: Changes in dependent module detected, Executing tests");
					}
				}
			}
		}

		return ConditionEvaluationResult.disabled(
				"ModulithExtension: No Changes detected in current module, executing tests");
	}

	private Set<JavaClass> getAllDependentClasses(ApplicationModule applicationModule,
			ApplicationModules applicationModules) {

		Set<ApplicationModule> dependentModules = new HashSet<>();
		dependentModules.add(applicationModule);
		this.getDependentModules(applicationModule, applicationModules, dependentModules);

		return dependentModules.stream()
				.map(appModule -> appModule.getDependencies(applicationModules))
				.flatMap(applicationModuleDependencies -> applicationModuleDependencies.stream()
						.map(ApplicationModuleDependency::getTargetType))
				.collect(Collectors.toSet());
	}

	private void getDependentModules(ApplicationModule applicationModule, ApplicationModules applicationModules,
			Set<ApplicationModule> modules) {

		Set<ApplicationModule> applicationModuleDependencies = applicationModule.getDependencies(applicationModules)
				.stream()
				.map(ApplicationModuleDependency::getTargetModule)
				.collect(Collectors.toSet());

		modules.addAll(applicationModuleDependencies);
		if (!applicationModuleDependencies.isEmpty()) {
			for (ApplicationModule applicationModuleDependency : applicationModuleDependencies) {
				this.getDependentModules(applicationModuleDependency, applicationModules, modules);
			}
		}
	}

}
