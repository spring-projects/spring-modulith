package org.springframework.modulith.events.jdbc;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DatabaseDriver;

class DatabaseTypeTest {

	@Test
	void shouldThrowExceptionOnUnsupportedDatabaseType() {
		assertThatThrownBy(() -> DatabaseType.from(DatabaseDriver.UNKNOWN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("UNKNOWN");
	}
}
