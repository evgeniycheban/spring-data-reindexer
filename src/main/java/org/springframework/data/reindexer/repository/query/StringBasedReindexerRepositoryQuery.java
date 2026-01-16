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
package org.springframework.data.reindexer.repository.query;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import ru.rt.restream.reindexer.Namespace;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.repository.util.PageableUtils;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter.QueryExpressionEvaluator;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Lazy;
import org.springframework.util.Assert;

/**
 * A string-based {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private static final String EXPRESSION_PARAMETER_PREFIX = "__$synthetic$__";

	private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)");

	private static final String[] OPERATORS = new String[] { "range", "in", "like", "is", "<", ">", "=" };

	private final ReindexerQueryMethod method;

	private final Namespace<?> namespace;

	private final QueryExpressionEvaluator queryEvaluator;

	private final QueryParameterMapper queryParameterMapper;

	private final Map<String, Integer> namedParameters;

	private final ReindexerConverter reindexerConverter;

	private final Lazy<BiFunction<ReindexerParameterAccessor, ReturnedType, Object>> queryExecution;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param accessor the {@link QueryMethodValueEvaluationContextAccessor} to use
	 * @param namespace the {@link Namespace} to use
	 * @param queryParameterMapper the {@link QueryParameterMapper} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public StringBasedReindexerRepositoryQuery(ReindexerQueryMethod method,
			QueryMethodValueEvaluationContextAccessor accessor, Namespace<?> namespace,
			QueryParameterMapper queryParameterMapper, ReindexerConverter reindexerConverter) {
		validate(method);
		this.method = method;
		this.reindexerConverter = reindexerConverter;
		this.namespace = namespace;
		this.queryEvaluator = createQueryExpressionEvaluator(method, accessor);
		this.queryParameterMapper = queryParameterMapper;
		this.namedParameters = new HashMap<>();
		for (Parameter parameter : method.getParameters()) {
			if (parameter.isNamedParameter()) {
				parameter.getName().ifPresent(name -> this.namedParameters.put(name, parameter.getIndex()));
			}
		}
		this.queryExecution = Lazy.of(() -> {
			if (method.isCollectionQuery()) {
				return (parameters, type) -> ReindexerQueryExecutions.toList(toIterator(parameters, type));
			}
			if (method.isPageQuery()) {
				return (parameters, type) -> {
					ProjectingResultIterator<?, ?> iterator = toIterator(parameters, type);
					return PageableExecutionUtils.getPage(ReindexerQueryExecutions.toList(iterator),
							parameters.getPageable(), iterator::getTotalCount);
				};
			}
			if (method.isSliceQuery()) {
				return (parameters, type) -> ReindexerQueryExecutions.toSlice(toIterator(parameters, type),
						parameters.getPageable());
			}
			if (method.isStreamQuery()) {
				return (parameters, type) -> ReindexerQueryExecutions.toStream(toIterator(parameters, type));
			}
			if (method.isIteratorQuery()) {
				return this::toIterator;
			}
			if (this.method.isModifyingQuery()) {
				return (parameters, type) -> {
					this.namespace.updateSql(prepareQuery(parameters));
					return null;
				};
			}
			return (parameters, type) -> ReindexerQueryExecutions.toEntity(toIterator(parameters, type));
		});
	}

	private void validate(ReindexerQueryMethod queryMethod) {
		if (queryMethod.isPageQuery()) {
			String query = queryMethod.getQuery().toLowerCase();
			if (!query.contains("count(*)") && !query.contains("count_cached(*)")) {
				throw new InvalidDataAccessApiUsageException(
						"Page query must contain COUNT or COUNT_CACHED expression for method: " + queryMethod);
			}
		}
	}

	private QueryExpressionEvaluator createQueryExpressionEvaluator(ReindexerQueryMethod method,
			QueryMethodValueEvaluationContextAccessor accessor) {
		ValueExpressionQueryRewriter queryRewriter = ValueExpressionQueryRewriter.of(ValueExpressionParser.create(),
				(index, expression) -> EXPRESSION_PARAMETER_PREFIX + index, String::concat);
		return queryRewriter.withEvaluationContextAccessor(accessor).parse(method.getQuery(), method.getParameters());
	}

	@Override
	public Object execute(Object[] parameters) {
		ReindexerParameterAccessor parameterAccessor = new ReindexerParameterAccessor(this.method.getParameters(),
				parameters);
		ResultProcessor resultProcessor = this.method.getResultProcessor().withDynamicProjection(parameterAccessor);
		Object result = this.queryExecution.get().apply(parameterAccessor, resultProcessor.getReturnedType());
		return resultProcessor.processResult(result);
	}

	private ProjectingResultIterator<?, ?> toIterator(ReindexerParameterAccessor parameters,
			ReturnedType returnedType) {
		String preparedQuery = prepareQuery(parameters);
		return new ProjectingResultIterator<>(this.namespace.execSql(preparedQuery), returnedType,
				this.reindexerConverter);
	}

	private String prepareQuery(ReindexerParameterAccessor parameters) {
		Map<String, Object> parameterMap = this.queryEvaluator.evaluate(parameters.getValues());
		StringBuilder result = new StringBuilder(this.queryEvaluator.getQueryString());
		char[] queryParts = this.queryEvaluator.getQueryString().toCharArray();
		int offset = 0;
		for (int i = 1; i < queryParts.length; i++) {
			char c = queryParts[i - 1];
			switch (c) {
				case ':', '?' -> {
					StringBuilder sb = new StringBuilder();
					for (int j = i; j < queryParts.length; j++) {
						if (!Character.isJavaIdentifierPart(queryParts[j])) {
							break;
						}
						sb.append(queryParts[j]);
					}
					String parameterReference = sb.toString();
					Object value = parameterMap.get(parameterReference);
					if (value == null) {
						if (c == ':') {
							Integer index = this.namedParameters.get(parameterReference);
							Assert.notNull(index, () -> "No parameter found for name: " + parameterReference);
							value = parameters.getBindableValue(index);
						}
						else {
							int index;
							try {
								index = Integer.parseInt(parameterReference);
							}
							catch (NumberFormatException e) {
								throw new IllegalStateException(
										"Invalid parameter reference: " + parameterReference + " at index: " + i);
							}
							value = parameters.getBindableValue(index - 1);
						}
					}
					int operatorIndex = -1;
					for (String operator : OPERATORS) {
						// Find the closest operator for this parameter reference.
						int found = StringUtils.lastIndexOfIgnoreCase(result, operator, i + offset - 1);
						if (found > operatorIndex) {
							operatorIndex = found;
						}
					}
					Assert.isTrue(operatorIndex != -1,
							() -> "Could not find conditional operator for parameter reference: " + parameterReference);
					// Find the index name before the conditional operator.
					String indexName = findIndexName(result, operatorIndex - 1);
					String valueString = getParameterValuePart(
							this.queryParameterMapper.mapParameterValue(indexName, value));
					result.replace(offset + i - 1, offset + i + parameterReference.length(), valueString);
					offset += valueString.length() - parameterReference.length() - 1;
					i += parameterReference.length();
				}
			}
		}
		if (StringUtils.indexOfIgnoreCase(result, "order by") == -1) {
			Sort sort = parameters.getSort();
			if (sort.isSorted()) {
				result.append(" order by ");
				for (Iterator<Order> orderIterator = sort.iterator(); orderIterator.hasNext();) {
					Order order = orderIterator.next();
					result.append(order.getProperty()).append(" ").append(order.getDirection());
					if (orderIterator.hasNext()) {
						result.append(", ");
					}
				}
			}
		}
		Pageable pageable = parameters.getPageable();
		if (pageable.isPaged()) {
			Matcher limitMatcher = LIMIT_PATTERN.matcher(result);
			int maxResults;
			if (limitMatcher.find()) {
				maxResults = Integer.parseInt(limitMatcher.group(1));
			}
			else {
				maxResults = method.isSliceQuery() ? pageable.getPageSize() + 1 : pageable.getPageSize();
				result.append(" limit ").append(maxResults);
			}
			if (StringUtils.indexOfIgnoreCase("offset", result) == -1) {
				int firstResult = PageableUtils.getOffsetAsInteger(pageable);
				if (firstResult > 0) {
					/*
					 * In order to return the correct results, we have to adjust the first
					 * result offset to be returned if: - a Pageable parameter is present
					 * - AND the requested page number > 0 - AND the requested page size
					 * was bigger than the derived result limitation via the First/Top
					 * keyword.
					 */
					if (pageable.getPageSize() > maxResults) {
						firstResult = firstResult - (pageable.getPageSize() - maxResults);
					}
					result.append(" offset ").append(firstResult);
				}
			}
		}
		return result.toString();
	}

	private String findIndexName(CharSequence charSequence, int start) {
		StringBuilder result = new StringBuilder();
		for (int j = start; j >= 0; j--) {
			char c = charSequence.charAt(j);
			if (Character.isJavaIdentifierPart(c)) {
				result.insert(0, c);
			}
			else {
				if (result.isEmpty()) {
					continue;
				}
				return result.toString();
			}
		}
		throw new IllegalArgumentException("Could not find index name starting at: " + start);
	}

	private String getParameterValuePart(Object value) {
		if (value instanceof String) {
			return "'" + value + "'";
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

	@Override
	public QueryMethod getQueryMethod() {
		return this.method;
	}

}
