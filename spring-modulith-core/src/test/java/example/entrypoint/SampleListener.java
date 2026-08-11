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
package example.entrypoint;

/**
 * An SPI exposed by the module (declared directly in the module's base package). Implementations may live in
 * internal, non-exposed packages, as callers are expected to depend on this interface, not the implementation.
 *
 * @author Oliver Drotbohm
 */
public interface SampleListener {

	void handle();
}
