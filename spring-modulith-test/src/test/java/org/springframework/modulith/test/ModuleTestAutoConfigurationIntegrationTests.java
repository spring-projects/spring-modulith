package org.springframework.modulith.test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests for {@link ModuleTestAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class ModuleTestAutoConfigurationIntegrationTests {

	@Test // GH-360
	void bootstrapsTestWithManualEntityScanOfModulithPackage() {

		var execution = mock(ModuleTestExecution.class);

		doReturn(Stream.of("org.springframework.modulith.test"))
				.when(execution).getBasePackages();

		new ApplicationContextRunner()
				.withBean(ModuleTestExecution.class, () -> execution)
				.withConfiguration(AutoConfigurations.of(ModuleTestAutoConfiguration.class))
				.withUserConfiguration(ManualModulithEntityScan.class)
				.run(ctx -> {

					assertThat(ctx).hasNotFailed();
					assertThat(ctx.getBean(EntityScanPackages.class).getPackageNames())
							.containsExactlyInAnyOrder("org.springframework.modulith.test",
									"org.springframework.modulith.events.jpa");
				});
	}

	@EntityScan("org.springframework.modulith.events.jpa")
	static class ManualModulithEntityScan {}
}
