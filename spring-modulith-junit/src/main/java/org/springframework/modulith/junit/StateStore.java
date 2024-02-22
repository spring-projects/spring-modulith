package org.springframework.modulith.junit;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.junit.Change.JavaClassChange;
import org.springframework.modulith.junit.Change.JavaTestClassChange;
import org.springframework.modulith.junit.Change.OtherFileChange;
import org.springframework.modulith.junit.diff.FileModificationDetector;
import org.springframework.modulith.junit.diff.ModifiedFilePath;
import org.springframework.util.ClassUtils;

class StateStore {
	private static final Logger log = LoggerFactory.getLogger(StateStore.class);

	private final ExtensionContext.Store store;

	StateStore(ExtensionContext context) {
		store = context.getRoot().getStore(Namespace.create(ModulithExecutionExtension.class));
	}

	Set<Class<?>> getChangedClasses() {
		// noinspection unchecked
		return (Set<Class<?>>) store.getOrComputeIfAbsent("changed-files", s -> {
			var environment = new StandardEnvironment();
			ConfigDataEnvironmentPostProcessor.applyTo(environment);

			var detector = FileModificationDetector.loadFileModificationDetector(environment);
			try {
				Set<ModifiedFilePath> modifiedFiles = detector.getModifiedFiles(environment);
				Set<Change> changes = Changes.toChanges(modifiedFiles);
				return toChangedClasses(changes);
			} catch (Exception e) {
				log.error("ModulithExecutionExtension: Unable to fetch changed files, executing all tests", e);
				return Set.of();
			}
		});
	}

	private static Set<Class<?>> toChangedClasses(Set<Change> changes) {
		Set<Class<?>> changedClasses = new HashSet<>();
		for (Change change : changes) {
			if (change instanceof OtherFileChange) {
				continue;
			}

			String className;
			if (change instanceof JavaClassChange jcc) {
				className = jcc.fullyQualifiedClassName();
			} else if (change instanceof JavaTestClassChange jtcc) {
				className = jtcc.fullyQualifiedClassName();
			} else {
				throw new IllegalStateException("Unexpected change type: " + change.getClass());
			}

			try {
				Class<?> aClass = ClassUtils.forName(className, null);
				changedClasses.add(aClass);
			} catch (ClassNotFoundException e) {
				log.trace("ModulithExecutionExtension: Unable to find class \"{}\"", className);
			}
		}
		return changedClasses;
	}
}
