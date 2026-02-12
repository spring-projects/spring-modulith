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
package org.springframework.modulith.core.util;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Helper to render a {@link Map} as JSON. Key need to be {@link #toString()}-able, values need to be
 * {@link #toString()}-able, too, be an enum, a {@link Collection} or {@link Map}.
 *
 * @author Oliver Drotbohm
 */
class Json {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static String toString(Object object) {

		if (object instanceof String string) {
			return quote(string);
		}

		if (object instanceof Collection collection) {
			return toString(collection);
		}

		if (object instanceof Map map) {
			return toString(map);
		}

		if (object instanceof Enum value) {
			return quote(value.name());
		}

		return object.toString();
	}

	private static String toString(Map<Object, Object> map) {

		return map.isEmpty()
				? "{}"
				: map.entrySet().stream()
						.map(Json::toString)
						.collect(Collectors.joining(", ", "{ ", " }"));
	}

	private static String toString(Entry<Object, Object> entry) {
		return "\"" + entry.getKey() + "\" : " + toString(entry.getValue());
	}

	private static String toString(Collection<Object> collection) {

		return collection.isEmpty()
				? "[]"
				: collection.stream().map(Json::toString).collect(Collectors.joining(", ", "[ ", " ]"));
	}

	private static String quote(String string) {
		return "\"" + string + "\"";
	}
}
