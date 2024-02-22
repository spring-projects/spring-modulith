package org.springframework.modulith.junit;

sealed interface Change {
	record JavaClassChange(String fullyQualifiedClassName) implements Change {}

	record JavaTestClassChange(String fullyQualifiedClassName) implements Change {}

	record OtherFileChange(String path) implements Change {}
}
