/*
 * Copyright 2017-2024 the original author or authors.
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
package org.springframework.modulith.events.support.restart;

import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.time.Duration;

/**
 * uses Spring Integration's {@link LockRegistry} to obtain
 * an exclusive, cluster-wide lock before resubmitting incomplete event publications.
 *
 * @author Josh Long
 */
public class ExclusiveIncompleteEventPublicationsProcessor extends DefaultIncompleteEventPublicationsProcessor
        implements IncompleteEventPublicationsProcessor {

    private final String lockName;
    private final long timeoutInMilliseconds;
    private final LockRegistry registry;

    public ExclusiveIncompleteEventPublicationsProcessor(
            boolean republishOnRestart, String lockName, long timeoutInMilliseconds, IncompleteEventPublications publications, LockRegistry registry) {
        super(republishOnRestart, publications);
        this.lockName = lockName;
        this.timeoutInMilliseconds = timeoutInMilliseconds;
        this.registry = registry;
    }
    
    @Override
    public void process() throws Throwable {
        registry.executeLocked(lockName, Duration.ofMillis(timeoutInMilliseconds == -1 ? 1_000 : timeoutInMilliseconds), () -> {
            super.process();
            return null;
        });
    }


}
