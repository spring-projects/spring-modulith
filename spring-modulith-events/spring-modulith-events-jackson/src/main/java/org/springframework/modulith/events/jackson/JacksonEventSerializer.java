/*
 * Copyright 2017-2026 the original author or authors.
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
package org.springframework.modulith.events.jackson;

import tools.jackson.databind.json.JsonMapper;

import java.util.function.Supplier;

import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.util.Assert;

/**
 * A Jackson-based {@link EventSerializer}.
 *
 * @author Oliver Drotbohm
 */
class JacksonEventSerializer implements EventSerializer {

	private final Supplier<JsonMapper> mapper;

	/**
	 * Creates a new {@link JacksonEventSerializer} for the given {@link JsonMapper}.
	 *
	 * @param mapper must not be {@literal null}.
	 */
	public JacksonEventSerializer(Supplier<JsonMapper> mapper) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");

		this.mapper = mapper;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventSerializer#serialize(java.lang.Object)
	 */
	@Override
	public Object serialize(Object event) {
		return mapper.get().writeValueAsString(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventSerializer#deserialize(java.lang.Object, java.lang.Class)
	 */
	@Override
	public <T> T deserialize(Object serialized, Class<T> type) {
		return mapper.get().readerFor(type).readValue(serialized.toString());
	}
}
