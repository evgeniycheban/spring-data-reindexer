/*
 * Copyright 2022 evgeniycheban
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.reindexer.core.convert;

import java.util.Collections;
import java.util.Map;

import org.springframework.core.env.EnvironmentCapable;
import org.springframework.data.expression.ValueEvaluationContext;
import org.springframework.data.expression.ValueExpression;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.spel.EvaluationContextProvider;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerExpressionEvaluatorFactory {

	private final ConcurrentLruCache<String, ValueExpression> expressionCache;

	private final EnvironmentCapable environmentProvider;

	private final EvaluationContextProvider evaluationContextProvider;

	ReindexerExpressionEvaluatorFactory(ExpressionParser expressionParser, EnvironmentCapable environmentProvider,
			EvaluationContextProvider evaluationContextProvider) {
		this(expressionParser, environmentProvider, evaluationContextProvider, 256);
	}

	ReindexerExpressionEvaluatorFactory(ExpressionParser expressionParser, EnvironmentCapable environmentProvider,
			EvaluationContextProvider evaluationContextProvider, int cacheSize) {
		Assert.notNull(expressionParser, "ExpressionParser must not be null");
		ValueExpressionParser parser = ValueExpressionParser.create(() -> expressionParser);
		this.expressionCache = new ConcurrentLruCache<>(cacheSize, parser::parse);
		this.environmentProvider = environmentProvider;
		this.evaluationContextProvider = evaluationContextProvider;
	}

	ReindexerExpressionEvaluator create(Object source) {
		return new ReindexerExpressionEvaluator() {
			@Override
			public <T> T evaluate(String expression) {
				return evaluate(expression, Collections.emptyMap());
			}

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(String expression, Map<String, Object> variables) {
				ValueExpression valueExpression = ReindexerExpressionEvaluatorFactory.this.expressionCache
					.get(expression);
				EvaluationContext evaluationContext = ReindexerExpressionEvaluatorFactory.this.evaluationContextProvider
					.getEvaluationContext(source, valueExpression.getExpressionDependencies());
				variables.forEach(evaluationContext::setVariable);
				ValueEvaluationContext ctx = ValueEvaluationContext.of(
						ReindexerExpressionEvaluatorFactory.this.environmentProvider.getEnvironment(),
						evaluationContext);
				return (T) valueExpression.evaluate(ctx);
			}
		};
	}

}
