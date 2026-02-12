/*
 * Copyright 2024-2026 the original author or authors.
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
package contributed;

import java.util.List;

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.ApplicationModuleSourceFactory;

/**
 * Example {@link ApplicationModuleSourceFactory}.
 *
 * @author Oliver Drotbohm
 */
public class ApplicationModuleSourceContribution implements ApplicationModuleSourceFactory {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ApplicationModuleSourceFactory#getRootPackages()
	 */
	@Override
	public List<String> getRootPackages() {
		return List.of("contributed");
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ApplicationModuleSourceFactory#getApplicationModuleDetectionStrategy()
	 */
	@Override
	public ApplicationModuleDetectionStrategy getApplicationModuleDetectionStrategy() {
		return ApplicationModuleDetectionStrategy.explicitlyAnnotated();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ApplicationModuleSourceFactory#getModuleBasePackages()
	 */
	@Override
	public List<String> getModuleBasePackages() {
		return List.of("contributed.enumerated");
	}
}
