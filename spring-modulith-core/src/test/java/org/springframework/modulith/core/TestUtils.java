/*
 * Copyright 2020-2023 the original author or authors.
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
package org.springframework.modulith.core;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.springframework.data.repository.Repository;
import org.springframework.util.Assert;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * Utilities for testing.
 *
 * @author Oliver Drotbohm
 */
class TestUtils {

	private static Supplier<JavaClasses> imported = Suppliers.memoize(() -> new ClassFileImporter() //
			.importPackagesOf(ApplicationModules.class, Repository.class, AggregateRoot.class));

	private static DescribedPredicate<JavaClass> IS_MODULE_TYPE = JavaClass.Predicates
			.resideInAPackage(ApplicationModules.class.getPackage().getName());

	private static Supplier<Classes> classes = Suppliers.memoize(() -> Classes.of(imported.get()).that(IS_MODULE_TYPE));

	/**
	 * Returns all {@link Classes} of this module.
	 *
	 * @return
	 */
	public static Classes getClasses() {
		return classes.get();
	}

	public static JavaClasses getJavaClasses() {
		return imported.get().that(IS_MODULE_TYPE);
	}

	/**
	 * Returns all {@link Classes} in the package of the given type.
	 *
	 * @param packageType must not be {@literal null}.
	 * @return
	 */
	public static Classes getClasses(Class<?> packageType) {

		Assert.notNull(packageType, "Package type must not be null!");

		return getClasses().that(resideInAPackage(packageType.getPackage().getName()));
	}
}
