/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.events.couchbase;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.transaction.CouchbaseCallbackTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Auto-configuration to enable Couchbase transaction management as that is required for the
 * {@link org.springframework.modulith.events.core.EventPublicationRegistry} to work properly.
 *
 * @author Oliver Drotbohm
 * @author Alexandre Vigneron
 */
@AutoConfiguration
@AutoConfigureBefore(TransactionAutoConfiguration.class)
@ConditionalOnProperty(
		name = "spring.modulith.events.couchbase.transaction-management.enabled",
		havingValue = "true",
		matchIfMissing = true)
class CouchbaseTransactionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	CouchbaseCallbackTransactionManager transactionManager(CouchbaseClientFactory factory) {
		return new CouchbaseCallbackTransactionManager(factory);
	}

	@Bean
	@ConditionalOnMissingBean
	TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
		return new TransactionTemplate(txManager);
	}
}
