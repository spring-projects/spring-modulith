package org.springframework.modulith.actuator.autoconfigure;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.actuator.ApplicationModulesEndpoint;

/**
 * Integration tests for {@link ApplicationModulesEndpointConfiguration}.
 *
 * @author Oliver Drotbohm
 */
@SpringBootTest
class ApplicationModulesEndpointConfigurationIntegrationTests {

	@SpringBootApplication
	static class SampleApp {}

	@Autowired ApplicationContext context;

	@Test // GH-87
	void bootstrapRegistersRuntimeInstances() {
		assertThat(context.getBean(ApplicationModulesEndpoint.class)).isNotNull();
	}
}
