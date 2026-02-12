/*
 * Copyright 2024-2026 the original author or authors.
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.modulith.events.EventExternalizationConfiguration.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.RoutingTarget;
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

	@Test // GH-1370
	public void testOnlyOneDownstreamExecutionAtATime() throws InterruptedException {

		var numberOfInvocations = 3;
		var events = Collections.synchronizedList(new ArrayList<String>());
		var complete = new CountDownLatch(numberOfInvocations);
		var counter = new AtomicInteger(0);
		var mock = mock(Downstream.class);

		when(mock.someMethod(any(RoutingTarget.class), any(Object.class))).thenAnswer(invocation -> {

			var callNumber = counter.incrementAndGet();

			events.add("CALL-" + callNumber);

			return CompletableFuture.supplyAsync(() -> {

				try {
					// Simulate some work in the CompletableFuture
					Thread.sleep(200);

					events.add("COMPLETE-" + callNumber);

					return "result-" + callNumber;

				} catch (InterruptedException e) {
					return "interrupted";
				}
			});
		});

		var configuration = externalizing()
				.select(annotatedAsExternalized())
				.serializeExternalization(true)
				.build();

		var service = new DelegatingEventExternalizer(configuration, mock::someMethod);

		for (int i = 0; i < numberOfInvocations; i++) {

			final var input = new Sample();

			new Thread(() -> {

				try {
					service.externalize(input).get();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					complete.countDown();
				}

			}).start();
		}

		complete.await(1, TimeUnit.SECONDS);

		assertThat(events).containsExactly("CALL-1", "COMPLETE-1", "CALL-2", "COMPLETE-2", "CALL-3", "COMPLETE-3");

		verify(mock, times(3)).someMethod(any(RoutingTarget.class), any(Object.class));
	}

	interface Downstream {
		CompletableFuture<?> someMethod(RoutingTarget target, Object event);
	}

	@Externalized
	static class Sample {}
}
