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
package org.springframework.modulith.events.neo4j;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * Automatically creates an index on the {@link Neo4jEventPublication#eventHash} field.
 *
 * @author Gerrit Meier
 * @since 1.1
 */
class Neo4jIndexInitializer implements InitializingBean {

	private final Neo4jClient neo4jClient;

	Neo4jIndexInitializer(Neo4jClient neo4jClient) {
		this.neo4jClient = neo4jClient;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {

		neo4jClient
				.query("CREATE INDEX eventHashIndex IF NOT EXISTS FOR (n:`Neo4jEventPublication`) ON (n.eventHash)")
				.run();
	}
}
