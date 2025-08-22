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

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;

/**
 * A {@link TypeExcludeFilter} that only selects types included in a {@link ModuleTestExecution}, i.e. from the modules
 * included in a particular test run.
 *
 * @author Oliver Drotbohm
 */
class ModuleTypeExcludeFilter extends TypeExcludeFilter {

	private final Supplier<ModuleTestExecution> execution;

	public ModuleTypeExcludeFilter(Class<?> testClass) {

		Assert.notNull(testClass, "Test class must not be null!");

		this.execution = ModuleTestExecution.of(testClass);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.boot.context.TypeExcludeFilter#match(org.springframework.core.type.classreading.MetadataReader, org.springframework.core.type.classreading.MetadataReaderFactory)
	 */
	@Override
	public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
		return execution.get().includes(metadataReader.getClassMetadata().getClassName());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ModuleTypeExcludeFilter that)) {
			return false;
		}

		return Objects.equals(execution.get(), that.execution.get());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(execution.get());
	}
}
