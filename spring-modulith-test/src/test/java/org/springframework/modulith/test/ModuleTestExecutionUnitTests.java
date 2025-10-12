/*
 * Copyright 2018-2025 the original author or authors.
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

import example.module.SampleTestA;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModuleTestExecution}.
 *
 * @author Yevhenii Semenov
 */
class ModuleTestExecutionUnitTests {

    @Test
    void concurrentAccessToExecutionCacheIsSafe() throws InterruptedException {
        int threadCount = 10;
        var startBarrier = new CyclicBarrier(threadCount);
        var completionLatch = new CountDownLatch(threadCount);
        var errors = Collections.synchronizedList(new ArrayList<Exception>());
        var executions = new ConcurrentHashMap<Integer, ModuleTestExecution>();

        var executorService = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                int threadId = i;
                executorService.submit(() -> {
                    try {
                        // Synchronize thread start for maximum contention
                        startBarrier.await();

                        // Get the execution
                        var execution = ModuleTestExecution.of(SampleTestA.class).get();
                        executions.put(threadId, execution);

                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            // Wait for all threads to complete
            assertThat(completionLatch.await(5, TimeUnit.SECONDS))
                .as("All threads should complete within 5 seconds")
                .isTrue();

            // Check for errors
            assertThat(errors)
                .as("No exceptions during concurrent access")
                .isEmpty();

            // Verify all threads got the same cached instance
            assertThat(executions).hasSize(threadCount);
            var firstExecution = executions.get(0);
            assertThat(executions.values())
                .allSatisfy(execution -> assertThat(execution).isSameAs(firstExecution));

        } finally {
            executorService.shutdown();
        }
    }
}
