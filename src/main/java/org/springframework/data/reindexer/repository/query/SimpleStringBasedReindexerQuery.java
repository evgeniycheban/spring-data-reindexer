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
package org.springframework.data.reindexer.repository.query;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiFunction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.vector.params.KnnSearchParam;

import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter.QueryExpressionEvaluator;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter.ParsedQuery;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Lazy;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;

/**
 * A simple string-based {@link RepositoryQuery} implementation that provides only
 * parameter binding, with no support for parameter type conversion. This is mainly used
 * as a fallback when {@link StringBasedReindexerQuery} cannot be used by the application.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class SimpleStringBasedReindexerQuery implements RepositoryQuery {

	private static final String EXPRESSION_PARAMETER_KEY_PREFIX = "__$synthetic$__";

	private static final ValueExpressionQueryRewriter QUERY_REWRITER = ValueExpressionQueryRewriter.of(
			ValueExpressionParser.create(), (index, expression) -> EXPRESSION_PARAMETER_KEY_PREFIX + index,
			String::concat);

	private static final ConcurrentLruCache<String, ParsedQuery> CACHE = new ConcurrentLruCache<>(64,
			QUERY_REWRITER::parse);

	private final ReindexerQueryMethod method;

	private final ReindexerConverter reindexerConverter;

	private final Namespace<?> namespace;

	private final Lazy<QueryExpressionEvaluator> queryEvaluator;

	private final Lazy<Map<String, Integer>> namedParameters;

	private final Lazy<BiFunction<ReindexerParameterAccessor, ReturnedType, Object>> queryExecution;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 * @param namespace the {@link Namespace} to use
	 * @param accessor the {@link QueryMethodValueEvaluationContextAccessor} to use
	 */
	public SimpleStringBasedReindexerQuery(ReindexerQueryMethod method, ReindexerConverter reindexerConverter,
			Namespace<?> namespace, QueryMethodValueEvaluationContextAccessor accessor) {
		this.method = method;
		this.reindexerConverter = reindexerConverter;
		this.namespace = namespace;
		this.queryEvaluator = Lazy.of(() -> createQueryEvaluator(method, accessor));
		this.namedParameters = Lazy.of(() -> getNamedParameters(method));
		this.queryExecution = Lazy.of(() -> getQueryExecution(method));
	}

	@Override
	public @Nullable Object execute(Object @NonNull [] parameters) {
		ReindexerParameterAccessor accessor = new ReindexerParameterAccessor(this.method.getParameters(), parameters);
		ResultProcessor resultProcessor = this.method.getResultProcessor().withDynamicProjection(accessor);
		Object result = this.queryExecution.get().apply(accessor, resultProcessor.getReturnedType());
		return resultProcessor.processResult(result);
	}

	@Override
	public @NonNull QueryMethod getQueryMethod() {
		return this.method;
	}

	private QueryExpressionEvaluator createQueryEvaluator(ReindexerQueryMethod method,
			QueryMethodValueEvaluationContextAccessor accessor) {
		ParsedQuery parsedQuery = CACHE.get(method.getQuery());
		return QUERY_REWRITER.new QueryExpressionEvaluator(accessor.create(method.getParameters()), parsedQuery);
	}

	private Map<String, Integer> getNamedParameters(ReindexerQueryMethod method) {
		Map<String, Integer> namedParameters = new HashMap<>();
		for (ReindexerParameter parameter : method.getParameters()) {
			if (parameter.isNamedParameter()) {
				parameter.getName().ifPresent(name -> namedParameters.put(name, parameter.getIndex()));
			}
		}
		// Add Vector and KnnSearchParam parameters to named parameters.
		if (method.getParameters().hasVectorParameter()) {
			ReindexerParameter parameter = method.getParameters().getParameter(method.getParameters().getVectorIndex());
			parameter.getName().ifPresent(name -> namedParameters.put(name, parameter.getIndex()));
		}
		if (method.getParameters().hasKnnSearchParam()) {
			ReindexerParameter parameter = method.getParameters()
				.getParameter(method.getParameters().getKnnSearchParamIndex());
			parameter.getName().ifPresent(name -> namedParameters.put(name, parameter.getIndex()));
		}
		return Collections.unmodifiableMap(namedParameters);
	}

	private BiFunction<ReindexerParameterAccessor, ReturnedType, Object> getQueryExecution(
			ReindexerQueryMethod method) {
		if (method.isSearchQuery()) {
			return getSearchQueryExecution(method);
		}
		if (method.isCollectionQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toList(toResultAccessor(parameters, returnedType));
		}
		if (method.isPageQuery()) {
			return (parameters, returnedType) -> {
				ReindexerResultAccessor<?> it = toResultAccessor(parameters, returnedType);
				return PageableExecutionUtils.getPage(ReindexerQueryExecutions.toList(it), parameters.getPageable(),
						it::getTotalCount);
			};
		}
		if (method.isSliceQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toSlice(toResultAccessor(parameters, returnedType), parameters.getPageable());
		}
		if (method.isStreamQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toStream(toResultAccessor(parameters, returnedType));
		}
		if (method.isIteratorQuery()) {
			return this::toResultAccessor;
		}
		if (method.isModifyingQuery()) {
			return (parameters, returnedType) -> {
				this.namespace.updateSql(prepareQuery(parameters));
				return null;
			};
		}
		return (parameters, returnedType) -> ReindexerQueryExecutions
			.toEntity(toResultAccessor(parameters, returnedType));
	}

	@SuppressWarnings("unchecked")
	private BiFunction<ReindexerParameterAccessor, ReturnedType, Object> getSearchQueryExecution(
			ReindexerQueryMethod method) {
		if (method.isStreamQuery()) {
			return (parameters, returnedType) -> {
				ReindexerResultAccessor<?> it = toResultAccessor(parameters, returnedType);
				return ReindexerQueryExecutions.toStream(it).map((e) -> new SearchResult<>(e, it.getCurrentRank()));
			};
		}
		if (method.isCollectionQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toSearchResults(toResultAccessor(parameters, returnedType), List.class);
		}
		return (parameters,
				returnedType) -> new SearchResults<>((List<? extends SearchResult<Object>>) ReindexerQueryExecutions
					.toSearchResults(toResultAccessor(parameters, returnedType), List.class));
	}

	private ReindexerResultAccessor<?> toResultAccessor(ReindexerParameterAccessor parameters,
			ReturnedType returnedType) {
		String preparedQuery = prepareQuery(parameters);
		return new ProjectingResultIterator<>(this.namespace.execSql(preparedQuery), returnedType,
				this.reindexerConverter);
	}

	private String prepareQuery(ReindexerParameterAccessor parameters) {
		QueryExpressionEvaluator queryEvaluator = this.queryEvaluator.get();
		Map<String, Object> resolvedValues = queryEvaluator.evaluate(parameters.getValues());
		ParsedQuery parsedQuery = CACHE.get(this.method.getQuery());
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
						Integer index = this.namedParameters.get().get(parameterReference);
						Assert.notNull(index,
								() -> "Could not resolve parameter: %s at: %d".formatted(parameterReference, start));
						value = parameters.getValue(index);
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

	private String getParameterValuePart(Object value) {
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

}
