/*
 * Copyright 2025 the original author or authors.
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
package org.springframework.modulith.runtime.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Spring modulith's runtime support.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 * @soundtrack Phil Siemers - Was wenn doch (Was wenn doch)
 */
@ConfigurationProperties("spring.modulith.runtime")
class SpringModulithRuntimeProperties {

	/**
	 * Whether the application modules arrangement is supposed to be verified on startup. Defaults to {@literal false}.
	 */
	private final boolean verificationEnabled;

	@ConstructorBinding
	SpringModulithRuntimeProperties(@DefaultValue("false") boolean verificationEnabled) {
		this.verificationEnabled = verificationEnabled;
	}

	public boolean isVerificationEnabled() {
		return verificationEnabled;
	}
}
