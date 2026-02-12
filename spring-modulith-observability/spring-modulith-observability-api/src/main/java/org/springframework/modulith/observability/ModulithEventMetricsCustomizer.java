/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.modulith.observability;

/**
 * Allows customizing the metrics creation, in particular the counters created for cross-application-module events.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
public interface ModulithEventMetricsCustomizer {

	/**
	 * Customize the given {@link ModulithEventMetrics}.
	 *
	 * @param metrics will never be {@literal null}.
	 */
	void customize(ModulithEventMetrics metrics);
}
