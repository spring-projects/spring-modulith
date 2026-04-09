/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.events.support;

import java.util.concurrent.CompletableFuture;

import org.springframework.modulith.events.RoutingTarget;

/**
 * SPI to capture the strategy to externalize the given event payload using a particular implementation technology,
 * usually a message broker.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Ron Spielman - Let My Love Rain Down On You - https://www.youtube.com/watch?v=1xWLQNMm44c
 */
public interface EventExternalizationTransport {

	/**
	 * Execute the actual externalization using a particular messaging infrastructure.
	 *
	 * @param payload will never be {@literal null}.
	 * @param target will never be {@literal null}.
	 * @return must not be {@literal null}.
	 */
	CompletableFuture<?> externalize(Object payload, RoutingTarget target);
}
