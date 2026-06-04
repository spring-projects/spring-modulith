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
package org.springframework.modulith.events.neo4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.util.Assert;

/**
 * Computes SHA-256 and MD5 hashes.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
abstract class EventHash {

	private static final String SHA_256 = "SHA-256";
	private static final String MD5 = "MD5";
	private static final HexFormat HEX = HexFormat.of();

	private EventHash() {}

	/**
	 * Returns the hex-encoded SHA-256 digest of the given bytes.
	 *
	 * @param bytes must not be {@literal null}.
	 */
	static String sha256(byte[] bytes) {

		Assert.notNull(bytes, "Bytes must not be null!");

		return digest(SHA_256, bytes);
	}

	/**
	 * Returns the hex-encoded MD5 digest of the given bytes.
	 *
	 * @param bytes must not be {@literal null}.
	 */
	static String md5(byte[] bytes) {

		Assert.notNull(bytes, "Bytes must not be null!");

		return digest(MD5, bytes);
	}

	/**
	 * Returns all hashes for the given byte array.
	 *
	 * @param bytes must not be {@literal null}.
	 */
	static List<String> candidates(byte[] bytes) {

		Assert.notNull(bytes, "Bytes must not be null!");

		return List.of(digest(SHA_256, bytes), digest(MD5, bytes));
	}

	private static String digest(String algorithm, byte[] bytes) {

		try {
			return HEX.formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
		} catch (NoSuchAlgorithmException o_O) {
			throw new IllegalStateException(algorithm + " not available", o_O);
		}
	}
}
