/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.events.jobrunr;

import java.util.List;

import org.jobrunr.scheduling.JobScheduler;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * A {@link ConditionalEventListener} scheduling a JobRunr job to eventually externalize the received event.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
class JobRunrEventExternalizer
		implements ApplicationListener<PayloadApplicationEvent<?>>, ConditionalEventListener {

	private final JobScheduler scheduler;
	private final List<JobRunrExternalizationTransport> externalizers;
	private final EventExternalizationConfiguration configuration;

	/**
	 * Creates a new {@link JobRunrEventExternalizer} for the given {@link JobScheduler},
	 * {@link JobRunrExternalizationTransport}s and {@link EventExternalizationConfiguration}.
	 *
	 * @param scheduler must not be {@literal null}.
	 * @param externalizers must not be {@literal null}.
	 * @param configuration must not be {@literal null}.
	 */
	JobRunrEventExternalizer(JobScheduler scheduler, List<JobRunrExternalizationTransport> externalizers,
			EventExternalizationConfiguration configuration) {

		Assert.notNull(scheduler, "JobScheduler must not be null!");
		Assert.notNull(externalizers, "JobRunrExternalizationTransports must not be null!");
		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");

		this.scheduler = scheduler;
		this.externalizers = externalizers;
		this.configuration = configuration;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.ConditionalEventListener#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(Object event) {
		return configuration.supports(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
	 */
	@Override
	@Transactional
	public void onApplicationEvent(PayloadApplicationEvent<?> event) {

		var payload = event.getPayload();

		scheduler.enqueue(externalizers.stream(), it -> it.externalize(payload));
	}
}
