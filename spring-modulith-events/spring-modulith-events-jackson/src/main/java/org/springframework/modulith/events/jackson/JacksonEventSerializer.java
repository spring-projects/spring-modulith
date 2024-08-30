/*
 * Copyright 2017-2024 the original author or authors.
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

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A Jackson-based {@link EventSerializer}.
 *
 * @author Oliver Drotbohm
 */
class JacksonEventSerializer implements EventSerializer {

	private final Supplier<ObjectMapper> mapper;

	/**
	 * Creates a new {@link JacksonEventSerializer} for the given {@link ObjectMapper}.
	 *
	 * @param mapper must not be {@literal null}.
	 */
	public JacksonEventSerializer(Supplier<ObjectMapper> mapper) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");

		this.mapper = mapper;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventSerializer#serialize(java.lang.Object)
	 */
	@Override
	public Object serialize(Object event) {

		try {
			return mapper.get().writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventSerializer#deserialize(java.lang.Object, java.lang.Class)
	 */
	@Override
	public <T> T deserialize(Object serialized, Class<T> type) {

		try {
			return mapper.get().readerFor(type).readValue(serialized.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
