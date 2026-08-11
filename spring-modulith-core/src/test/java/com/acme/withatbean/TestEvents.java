/*
 * Copyright 2020-2026 the original author or authors.
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
package com.acme.withatbean;

import com.acme.other.ExternalConnector;

/**
 * @author Oliver Drotbohm
 */
public class TestEvents {

	/**
	 * Method calling a factory method.
	 */
	public void method() {
		JMoleculesAnnotated.of();
	}

	/**
	 * Method calling a constructor.
	 */
	public void constructorCall() {
		new JMoleculesAnnotated();
	}

	/**
	 * Calls a factory method just like {@link #method()} but, unlike that one, isn't publicly accessible itself and
	 * thus must not be considered an entry point even though it's declared on this otherwise exposed type.
	 */
	void packagePrivateMethod() {
		JMoleculesAnnotated.of();
	}

	/**
	 * Reaches {@link #constructorCall()} only via a detour through {@link ExternalConnector}, i.e. leaves the module and
	 * re-enters it elsewhere. Must not be considered an entry point for the event {@link #constructorCall()} creates, as
	 * that would require walking the call graph beyond this module's boundary.
	 */
	public void viaExternalRoute(ExternalConnector connector) {
		connector.relay(this);
	}

	// jMolecules

	@org.jmolecules.event.annotation.DomainEvent
	public static class JMoleculesAnnotated {
		public static JMoleculesAnnotated of() {
			return null;
		}
	}

	public static class JMoleculesImplementing implements org.jmolecules.event.types.DomainEvent {}
}
