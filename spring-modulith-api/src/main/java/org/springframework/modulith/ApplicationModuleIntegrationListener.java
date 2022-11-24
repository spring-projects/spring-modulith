/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.modulith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * An {@link ApplicationModuleIntegrationListener} is an {@link Async} Spring {@link TransactionalEventListener} that
 * runs in a transaction itself. Thus, the annotation serves as syntactic sugar for the generally recommend setup to
 * integrate application modules via events. The setup makes sure that an original business transaction completes
 * successfully and the integration asynchronously runs in a transaction itself to decouple the integration as much as
 * possible from the original unit of work.
 * <p>
 * It is advisable that you use these integration listeners in combination with the Spring Modulith Event Publication
 * Registry to make sure that the event publication does not get lost in case of an application or listener failure.
 *
 * @author Oliver Drotbohm
 * @see <a href="https://docs.spring.io/spring-modulith/docs/current/reference/html/#events.publication-registry">Spring
 *      Modulith Event Publication Registry - Reference Documentation</a>
 */
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener
@Documented
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplicationModuleIntegrationListener {

	/**
	 * Whether the transaction to be run for the event listener is supposed to be read-only (default {@literal false}).
	 */
	@AliasFor(annotation = Transactional.class, attribute = "readOnly")
	boolean readOnlyTransaction() default false;
}
