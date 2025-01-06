/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.modulith.events;

import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link String}-based routing target that supports a {@code ::} delimiter to separate the sole target from an
 * additional key. The key itself could be of any format and might be subject for deeper inspection downstream.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
public class RoutingTarget {

	private final String target;
	private final @Nullable String key;

	/**
	 * Creates a new {@link RoutingTarget} for the given target and key.
	 *
	 * @param target must not be {@literal null} or empty.
	 * @param key must not be {@literal null}.
	 */
	private RoutingTarget(String target, @Nullable String key) {

		Assert.hasText(target, "Target must not be null or empty!");

		this.target = target;
		this.key = key;
	}

	/**
	 * Creates a new {@link ParsedRoutingTarget} by parsing the given source.
	 *
	 * @param source must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static ParsedRoutingTarget parse(String source) {

		Assert.notNull(source, "Routing target source must not be null!");

		var parts = source.split("::", 2);
		var target = parts[0].isBlank() ? null : parts[0];
		var key = parts.length == 2 ? parts[1] : null;

		return new ParsedRoutingTarget(target, key);
	}

	/**
	 * Creates a new {@link RoutingTargetBuilder} for the given target.
	 *
	 * @param target must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static RoutingTargetBuilder forTarget(String target) {
		return new RoutingTargetBuilder(target);
	}

	/**
	 * An intermediary to ultimately create {@link RoutingTarget} instances.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.1
	 */
	public static class RoutingTargetBuilder {

		private final String target;

		/**
		 * Creates a new {@link RoutingTargetBuilder} for the given target.
		 *
		 * @param target will never be {@literal null} or empty.
		 */
		private RoutingTargetBuilder(String target) {

			Assert.hasText(target, "Target must not be null or empty!");

			this.target = target;
		}

		/**
		 * Returns a new {@link RoutingTarget} with the already configured target and the given key.
		 *
		 * @param key must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public RoutingTarget andKey(String key) {
			return new RoutingTarget(target, key);
		}

		/**
		 * Returns a new {@link RoutingTarget} without a key.
		 *
		 * @return will never be {@literal null}.
		 */
		public RoutingTarget withoutKey() {
			return new RoutingTarget(target, null);
		}
	}

	/**
	 * Returns the routing target.
	 *
	 * @return will never be {@literal null}.
	 */
	public String getTarget() {
		return target;
	}

	/**
	 * Returns the routing key.
	 *
	 * @return can be {@literal null}.
	 */
	@Nullable
	public String getKey() {
		return key;
	}

	/**
	 * Returns whether the routing key is a SpEL expression.
	 *
	 * @return whether the routing key is a SpEL expression.
	 */
	public boolean hasKeyExpression() {
		return key != null && key.startsWith("#{");
	}

	RoutingTarget withTarget(String target) {
		return new RoutingTarget(target, key);
	}

	/**
	 * Creates a new {@link RoutingTarget} with the same target but the given routing key.
	 *
	 * @param key can be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	RoutingTarget withKey(@Nullable String key) {
		return new RoutingTarget(target, key);
	}

	RoutingTarget verify() {

		if (target.isBlank()) {
			throw new IllegalStateException(
					"No target set! Make sure your externalization configuration always produces a target!");
		}

		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return target + "::" + (key == null ? "" : key);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof RoutingTarget that)) {
			return false;
		}

		return Objects.equals(this.target, that.target)
				&& Objects.equals(this.key, that.key);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(this.target, this.key);
	}

	/**
	 * A parsed routing target that can have {@literal null} target and key values. It can be turned into a constrained
	 * {@link RoutingTarget} by either explicitly expecting it to be valid (see {@link #toRoutingTarget()} or by providing
	 * a fallback {@link RoutingTarget} that would be used to fill in the blanks.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.1
	 * @see #toRoutingTarget()
	 * @see #withFallback(RoutingTarget)
	 */
	static class ParsedRoutingTarget {

		private final @Nullable String target, key;

		ParsedRoutingTarget(@Nullable String target, @Nullable String key) {
			this.target = target;
			this.key = key;
		}

		/**
		 * @return the target
		 */
		public String getTarget() {
			return target;
		}

		/**
		 * @return the key
		 */
		public String getKey() {
			return key;
		}

		RoutingTarget toRoutingTarget() {

			if (!StringUtils.hasText(target)) {
				throw new IllegalStateException("Routing target must not be empty!");
			}

			return new RoutingTarget(target, key);
		}

		RoutingTarget withFallback(RoutingTarget fallback) {

			var newTarget = StringUtils.hasText(target) ? target : fallback.getTarget();

			return new RoutingTarget(newTarget, key != null ? key : fallback.getKey());
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof ParsedRoutingTarget that)) {
				return false;
			}

			return Objects.equals(this.target, that.target)
					&& Objects.equals(this.key, that.key);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(target, key);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {

			return (target != null ? target : "")
					.concat("::")
					.concat(key != null ? key : "");
		}
	}
}
