package org.springframework.modulith.events.neo4j;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * @author Gerrit Meier
 */
public class Neo4jIndexInitializer implements InitializingBean {

	private final Neo4jClient neo4jClient;

	public Neo4jIndexInitializer(Neo4jClient neo4jClient) {
		this.neo4jClient = neo4jClient;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		neo4jClient
			.query("CREATE INDEX eventHashIndex IF NOT EXISTS FOR (n:`Neo4jEventPublication`) ON (n.eventHash)")
			.run();
	}
}
