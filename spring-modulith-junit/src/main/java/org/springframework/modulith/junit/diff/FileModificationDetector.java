package org.springframework.modulith.junit.diff;

import static org.springframework.modulith.junit.ModulithExecutionExtension.*;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.PropertyResolver;
import org.springframework.lang.NonNull;
import org.springframework.modulith.junit.ModulithExecutionExtension;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public interface FileModificationDetector {
	Logger log = LoggerFactory.getLogger(FileModificationDetector.class);

	String CLASS_FILE_SUFFIX = ".java";
	String PACKAGE_PREFIX = "src.main.java";

	Set<ModifiedFilePath> getModifiedFiles(@NonNull PropertyResolver propertyResolver)
			throws IOException, GitAPIException;

	static FileModificationDetector loadFileModificationDetector(@NonNull PropertyResolver propertyResolver) {
		var detectorClassName = propertyResolver.getProperty(CONFIG_PROPERTY_PREFIX + ".file-modification-detector");
		var referenceCommit = ReferenceCommitDetector.getReferenceCommitProperty(propertyResolver);

		if (StringUtils.hasText(detectorClassName)) {
			try {
				var strategyType = ClassUtils.forName(detectorClassName, ModulithExecutionExtension.class.getClassLoader());
				log.info("Found request via property for file modification detector '{}'", detectorClassName);
				return BeanUtils.instantiateClass(strategyType, FileModificationDetector.class);

			} catch (ClassNotFoundException | LinkageError o_O) {
				throw new IllegalStateException(o_O);
			}
		} else if (StringUtils.hasText(referenceCommit)) {
			log.info("Found reference commit property. Using file modification detector '{}'",
					ReferenceCommitDetector.class.getName());
			return new ReferenceCommitDetector();
		}

		log.info("Using default file modification detector '{}'", UnpushedCommitsDetector.class.getName());
		return new UnpushedCommitsDetector();
	}
}
