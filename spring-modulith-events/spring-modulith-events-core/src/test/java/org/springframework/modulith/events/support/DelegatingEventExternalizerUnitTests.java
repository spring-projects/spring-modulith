/*
 * Copyright 2024-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit tests for {@link DelegatingEventExternalizer}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class DelegatingEventExternalizerUnitTests {

	@Test // GH-859
	void doesNotRequireATransactionForExternalization() throws Exception {

		var method = DelegatingEventExternalizer.class.getMethod("externalize", Object.class);
		var annotation = AnnotatedElementUtils.getMergedAnnotation(method, Transactional.class);

		assertThat(annotation.propagation()).isEqualTo(Propagation.SUPPORTS);
	}
}
