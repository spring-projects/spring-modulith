/*
 * Copyright 2023-2025 the original author or authors.
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
package example.sample;

import org.springframework.aop.TargetClassAware;
import org.springframework.aop.framework.Advised;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author Oliver Drotbohm
 */
@Component
public abstract class ObservedComponent implements Advised, TargetClassAware {

	@Async
	public void someMethod() {}

	@SuppressWarnings("unused")
	private void someInternalMethod() {}

	@ApplicationModuleListener
	void on(Object event) {}
}
