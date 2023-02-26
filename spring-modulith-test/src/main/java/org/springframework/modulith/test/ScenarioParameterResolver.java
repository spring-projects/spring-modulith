/*
 * Copyright 2023 the original author or authors.
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

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JUnit {@link ParameterResolver} for {@link Scenario}.
 *
 * @author Oliver Drotbohm
 */
class ScenarioParameterResolver implements ParameterResolver, BeforeAllCallback {

	private final PublishedEventsParameterResolver delegate;

	/**
	 * Creates a new {@link ScenarioParameterResolver}.
	 */
	public ScenarioParameterResolver() {
		this.delegate = new PublishedEventsParameterResolver();
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.ParameterResolver#supportsParameter(org.junit.jupiter.api.extension.ParameterContext, org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {

		var type = parameterContext.getParameter().getType();

		return Scenario.class.isAssignableFrom(type);
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.BeforeAllCallback#beforeAll(org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		delegate.beforeAll(context);
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.ParameterResolver#resolveParameter(org.junit.jupiter.api.extension.ParameterContext, org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {

		var context = SpringExtension.getApplicationContext(extensionContext);
		var operations = context.getBean(TransactionTemplate.class);
		var events = (AssertablePublishedEvents) delegate.resolveParameter(parameterContext, extensionContext);

		return new Scenario(operations, context, events);
	}
}
