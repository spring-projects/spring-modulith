package org.springframework.modulith.events.neo4j;

import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;
import org.springframework.modulith.events.core.EventSerializer;

/**
 * @author Gerrit Meier
 */
@AutoConfiguration
@AutoConfigureBefore(EventPublicationAutoConfiguration.class)
public class Neo4jEventPublicationAutoConfiguration implements EventPublicationConfigurationExtension {

	@Bean
	Neo4jEventPublicationRepository neo4jEventPublicationRepository(Neo4jClient neo4jClient, Configuration cypherDslConfiguration, EventSerializer eventSerializer) {
		return new Neo4jEventPublicationRepository(neo4jClient, cypherDslConfiguration, eventSerializer);
	}

	@Bean
	@ConditionalOnMissingBean(Configuration.class)
	Configuration cypherDslConfiguration() {
		return Configuration.defaultConfig();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.modulith.events.neo4j.event-index.enabled", havingValue = "true")
	Neo4jIndexInitializer neo4jIndexInitializer(Neo4jClient neo4jClient) {
		return new Neo4jIndexInitializer(neo4jClient);
	}
}
