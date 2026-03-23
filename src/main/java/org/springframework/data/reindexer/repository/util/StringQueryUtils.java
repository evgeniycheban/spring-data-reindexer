/*
 * Copyright 2022-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.reindexer.repository.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

import ru.rt.restream.reindexer.vector.params.KnnSearchParam;

import org.springframework.data.domain.Vector;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.reindexer.repository.query.ReindexerParameterAccessor;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;
import org.springframework.util.ConcurrentLruCache;

/**
 * Provides utility methods to work with string-queries.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class StringQueryUtils {

	private static final ValueExpressionQueryRewriter QUERY_REWRITER = ValueExpressionQueryRewriter
		.of(ValueExpressionParser.create(), (index, expression) -> "__$synthetic$__" + index, String::concat);

	private static final ConcurrentLruCache<String, ValueExpressionQueryRewriter.ParsedQuery> CACHE = new ConcurrentLruCache<>(
			32, QUERY_REWRITER::parse);

	/**
	 * Substitutes named and positional parameters e.g., {@code :phoneNumber}, {@code ?1}
	 * with the values accessed via {@code ReindexerParameterAccessor}. Additionally,
	 * resolves SpEL-based parameters using
	 * {@code QueryMethodValueEvaluationContextAccessor}.
	 * @param query the string query to use
	 * @param parameters the {@link ReindexerParameterAccessor} to use
	 * @param factory the {@link QueryMethodValueEvaluationContextAccessor} to use
	 * @return the string query with substituted parameter references with the provided
	 * values
	 */
	public static String substituteQueryParameters(String query, ReindexerParameterAccessor parameters,
			QueryMethodValueEvaluationContextAccessor factory) {
		ValueExpressionQueryRewriter.ParsedQuery parsedQuery = CACHE.get(query);
		ValueExpressionQueryRewriter.QueryExpressionEvaluator evaluator = QUERY_REWRITER.new QueryExpressionEvaluator(
				factory.create(parameters.getParameters()), parsedQuery);
		Map<String, Object> resolvedValues = evaluator.evaluate(parameters.getValues());
		String queryString = parsedQuery.getQueryString();
		StringBuilder result = new StringBuilder(queryString.length());
		char[] queryParts = queryString.toCharArray();
		int i = 0;
		while (i < queryParts.length) {
			if (parsedQuery.isQuoted(i)) {
				result.append(queryParts[i++]);
				continue;
			}
			char c = queryParts[i];
			switch (c) {
				case ':', '?' -> {
					int start = i + 1;
					int j = start;
					while (j < queryParts.length && Character.isJavaIdentifierPart(queryParts[j])) {
						j++;
					}
					String parameterReference = queryString.substring(start, j);
					Object value;
					if (resolvedValues.containsKey(parameterReference)) {
						value = resolvedValues.get(parameterReference);
					}
					else if (c == ':') {
						value = parameters.getValue(parameterReference);
					}
					else {
						try {
							int index = Integer.parseInt(parameterReference);
							value = parameters.getValue(index - 1);
						}
						catch (NumberFormatException e) {
							throw new IllegalStateException(
									"Could not parse parameter: %s at: %d".formatted(parameterReference, start));
						}
					}
					result.append(getParameterValuePart(value));
					i = j;
				}
				default -> {
					result.append(c);
					i++;
				}
			}
		}
		return result.toString();

	}

	private static String getParameterValuePart(Object value) {
		if (value instanceof String) {
			return "'" + value + "'";
		}
		if (value instanceof Vector vector) {
			return Arrays.toString(vector.toFloatArray());
		}
		if (value instanceof float[] vector) {
			return Arrays.toString(vector);
		}
		if (value instanceof KnnSearchParam knnSearchParam) {
			return String.join(", ", knnSearchParam.toLog());
		}
		if (value instanceof Collection<?> values) {
			StringJoiner joiner = new StringJoiner(", ", "(", ")");
			for (Object object : values) {
				joiner.add(getParameterValuePart(object));
			}
			return joiner.toString();
		}
		if (value != null && value.getClass().isArray()) {
			StringJoiner joiner = new StringJoiner(", ", "(", ")");
			int length = Array.getLength(value);
			for (int i = 0; i < length; i++) {
				joiner.add(getParameterValuePart(Array.get(value, i)));
			}
			return joiner.toString();
		}
		return String.valueOf(value);
	}

	private StringQueryUtils() {
		// utils.
	}

}
