/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * Annotation to mark a package as named interface of a {@link Module} (either implicit or explicitly annotated).
 *
 * @author Oliver Drotbohm
 */
@Documented
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface NamedInterface {

	/**
	 * The name of the interface.
	 *
	 * @return
	 */
	String[] value();
}
