package org.springframework.modulith.events.jdbc;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DatabaseDriver;

class DatabaseTypeUnitTests {

	@Test // GH-29
	void shouldThrowExceptionOnUnsupportedDatabaseType() {

		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> DatabaseType.from(DatabaseDriver.UNKNOWN))
				.withMessageContaining("UNKNOWN");
	}
}
