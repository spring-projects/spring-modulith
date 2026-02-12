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
package org.springframework.modulith.events.support;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.util.Assert;

/**
 * A {@link BrokerRouting} supports {@link RoutingTarget} instances that contain values matching the format
 * {@code $target::$key} for which both the target and key can be a SpEL expression.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
public class BrokerRouting {

	protected final RoutingTarget target;

	/**
	 * Creates a new {@link BrokerRouting} for the given {@link RoutingTarget}.
	 *
	 * @param target must not be {@literal null}.
	 */
	private BrokerRouting(RoutingTarget target) {

		Assert.notNull(target, "RoutingTarget must not be null!");

		this.target = target;
	}

	/**
	 * Creates a new {@link BrokerRouting} for the given {@link RoutingTarget} and {@link EvaluationContext}.
	 *
	 * @param target must not be {@literal null}.
	 * @param context must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static BrokerRouting of(RoutingTarget target, EvaluationContext context) {

		return target.hasExpression()
				? new SpelBrokerRouting(target, context)
				: new BrokerRouting(target);
	}

	/**
	 * Returns the actual routing target for the given event. The default implementation will ignore the given parameter.
	 * Sub-types might choose to consider it, though.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	public String getTarget(Object event) {
		return target.getTarget();
	}

	/**
	 * Resolves the routing key against the given event. In case the original {@link RoutingTarget} contained an
	 * expression, the event will be used as root object to evaluate that expression.
	 *
	 * @param event must not be {@literal null}.
	 * @return can be {@literal null}.
	 */
	@Nullable
	public String getKey(Object event) {
		return target.getKey();
	}

	/**
	 * A {@link BrokerRouting} that evaluates a {@link RoutingTarget}'s key as SpEL expression.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.1
	 */
	static class SpelBrokerRouting extends BrokerRouting {

		private static final SpelExpressionParser PARSER = new SpelExpressionParser();
		private static final TemplateParserContext CONTEXT = new TemplateParserContext();

		private final Expression targetExpression;
		private final @Nullable Expression keyExpression;
		private final EvaluationContext context;

		/**
		 * Creates a new {@link SpelBrokerRouting} for the given {@link RoutingTarget} and {@link EvaluationContext}.
		 *
		 * @param target must not be {@literal null}.
		 * @param context must not be {@literal null}.
		 */
		@SuppressWarnings("null")
		private SpelBrokerRouting(RoutingTarget target, EvaluationContext context) {

			super(target);

			Assert.notNull(context, "EvaluationContext must not be null!");

			this.keyExpression = target.getKey() == null ? null : PARSER.parseExpression(target.getKey(), CONTEXT);
			this.targetExpression = PARSER.parseExpression(target.getTarget(), CONTEXT);
			this.context = context;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.support.BrokerRouting#getTarget(java.lang.Object)
		 */
		@Override
		public String getTarget(@Nullable Object event) {

			var result = targetExpression.getValue(context, event);

			if (result == null) {
				throw new IllegalStateException(
						"Evaluation of target expression %s must not result in null!".formatted(targetExpression));
			}

			return result.toString();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.support.BrokerRouting#getKey(java.lang.Object)
		 */
		@Nullable
		@Override
		public String getKey(Object event) {

			var expression = keyExpression;

			if (expression == null) {
				return target.getTarget();
			}

			var result = expression.getValue(context, event);

			return result == null ? null : result.toString();
		}
	}
}
