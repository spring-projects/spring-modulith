/*
 * Copyright 2020-2025 the original author or authors.
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
package com.acme.myproject.stereotypes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author Oliver Drotbohm
 */
public class Stereotypes {

	@Component
	static class SomeComponent {}

	@Controller
	static class SomeController {}

	@Service
	static class SomeService {}

	@Repository
	static class SomeRepository {}

	@Component
	static class SomeRepresentations {}

	@Component
	static class SomeEventListener {

		@EventListener
		void someEventListener(Object event) {}
	}

	@Component
	static class SomeTxEventListener {

		@TransactionalEventListener
		void someTxEventListener(Object event) {}
	}

	@Component // Used for documentation purposes
	public static interface SomeAppInterface {};

	@Component
	static class SomeAppInterfaceImplementation implements SomeAppInterface {}

	@ConfigurationProperties("org.springframework.modulith.sample")
	static record SomeConfigurationProperties(String test) {}
}
