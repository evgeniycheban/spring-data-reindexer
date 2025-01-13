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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.expression.ValueExpressionParser;
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
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private static final String EXPRESSION_PARAMETER_PREFIX = "__$synthetic$__";

	private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)");

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final QueryExpressionEvaluator queryEvaluator;

	private final Map<String, Integer> namedParameters;

	private final Lazy<QueryExecution> queryExecution;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link QueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param accessor the {@link QueryMethodValueEvaluationContextAccessor} to use
	 * @param reindexer the {@link Reindexer} to use
	 */
	public StringBasedReindexerRepositoryQuery(ReindexerQueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation,
			QueryMethodValueEvaluationContextAccessor accessor, Reindexer reindexer) {
		validate(queryMethod);
		this.queryMethod = queryMethod;
		this.namespace = reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.queryEvaluator = createQueryExpressionEvaluator(accessor);
		this.namedParameters = new HashMap<>();
		for (Parameter parameter : queryMethod.getParameters()) {
			if (parameter.isNamedParameter()) {
				parameter.getName().ifPresent(name -> this.namedParameters.put(name, parameter.getIndex()));
			}
		}
		this.queryExecution = Lazy.of(() -> {
			if (queryMethod.isCollectionQuery()) {
				return this::toList;
			}
			if (queryMethod.isPageQuery()) {
				return (parameters, returnedType) -> {
					try (ProjectingResultIterator it = toIterator(parameters, returnedType)) {
						return PageableExecutionUtils.getPage(toList(it), parameters.getPageable(), it::getTotalCount);
					}
				};
			}
			if (queryMethod.isStreamQuery()) {
				return this::toStream;
			}
			if (queryMethod.isIteratorQuery()) {
				return this::toIterator;
			}
			if (this.queryMethod.isModifyingQuery()) {
				return (parameters, returnedType) -> {
					this.namespace.updateSql(prepareQuery(parameters));
					return null;
				};
			}
			return (parameters, returnedType) -> {
				Object entity = toEntity(parameters, returnedType);
				if (queryMethod.isOptionalQuery()) {
					return Optional.ofNullable(entity);
				}
				Assert.state(entity != null, "Exactly one item expected, but there is zero");
				return entity;
			};
		});
	}

	private void validate(ReindexerQueryMethod queryMethod) {
		if (queryMethod.isPageQuery()) {
			String query = queryMethod.getQuery().toLowerCase();
			if (!query.contains("count(*)") && !query.contains("count_cached(*)")) {
				throw new InvalidDataAccessApiUsageException("Page query must contain COUNT or COUNT_CACHED expression for method: " + queryMethod);
			}
		}
	}

	private QueryExpressionEvaluator createQueryExpressionEvaluator(QueryMethodValueEvaluationContextAccessor accessor) {
		ValueExpressionQueryRewriter queryRewriter = ValueExpressionQueryRewriter
				.of(ValueExpressionParser.create(), (index, expression) -> EXPRESSION_PARAMETER_PREFIX + index, String::concat);
		return queryRewriter.withEvaluationContextAccessor(accessor).parse(queryMethod.getQuery(), queryMethod.getParameters());
	}

	@Override
	public Object execute(Object[] parameters) {
		ReindexerParameterAccessor parameterAccessor = new ReindexerParameterAccessor(this.queryMethod.getParameters(), parameters);
		ResultProcessor resultProcessor = this.queryMethod.getResultProcessor().withDynamicProjection(parameterAccessor);
		Object result = this.queryExecution.get().execute(parameterAccessor, resultProcessor.getReturnedType());
		return resultProcessor.processResult(result);
	}

	private Stream<Object> toStream(ReindexerParameterAccessor parameters, ReturnedType returnedType) {
		ProjectingResultIterator iterator = toIterator(parameters, returnedType);
		Spliterator<Object> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false);
	}

	private List<Object> toList(ReindexerParameterAccessor parameters, ReturnedType returnedType) {
		try (ProjectingResultIterator it = toIterator(parameters, returnedType)) {
			return toList(it);
		}
	}

	private List<Object> toList(ProjectingResultIterator iterator) {
		List<Object> result = new ArrayList<>();
		while (iterator.hasNext()) {
			result.add(iterator.next());
		}
		return result;
	}

	private Object toEntity(ReindexerParameterAccessor parameters, ReturnedType returnedType) {
		Object item = null;
		try (ProjectingResultIterator it = toIterator(parameters, returnedType)) {
			if (it.hasNext()) {
				item = it.next();
			}
			if (it.hasNext()) {
				throw new IllegalStateException("Exactly one item expected, but there are more");
			}
		}
		return item;
	}

	private ProjectingResultIterator toIterator(ReindexerParameterAccessor parameters, ReturnedType returnedType) {
		String preparedQuery = prepareQuery(parameters);
		return new ProjectingResultIterator(this.namespace.execSql(preparedQuery), returnedType);
	}

	private String prepareQuery(ReindexerParameterAccessor parameters) {
		Map<String, Object> parameterMap = this.queryEvaluator.evaluate(parameters.getValues());
		StringBuilder result = new StringBuilder(this.queryEvaluator.getQueryString().toLowerCase());
		char[] queryParts = this.queryEvaluator.getQueryString().toCharArray();
		int offset = 0;
		for (int i = 1; i < queryParts.length; i++) {
			char c = queryParts[i - 1];
			switch (c) {
				case ':', '?' -> {
					StringBuilder sb = new StringBuilder();
					for (int j = i; j < queryParts.length; j++) {
						if (Character.isWhitespace(queryParts[j])) {
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
								throw new IllegalStateException("Invalid parameter reference: " + parameterReference + " at index: " + i);
							}
							value = parameters.getBindableValue(index - 1);
						}
					}
					String valueString = getParameterValuePart(value);
					result.replace(offset + i - 1, offset + i + parameterReference.length(), valueString);
					offset += valueString.length() - parameterReference.length() - 1;
					i += parameterReference.length();
				}
			}
		}
		if (result.indexOf("order by") == -1) {
			Sort sort = parameters.getSort();
			if (sort.isSorted()) {
				result.append(" order by ");
				for (Iterator<Order> orderIterator = sort.iterator(); orderIterator.hasNext(); ) {
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
				maxResults = pageable.getPageSize();
				result.append(" limit ").append(maxResults);
			}
			if (result.indexOf("offset") == -1) {
				int firstResult = ReindexerQueryCreator.getOffsetAsInteger(pageable);
				if (firstResult > 0) {
					/*
					 * In order to return the correct results, we have to adjust the first result offset to be returned if:
					 * - a Pageable parameter is present
					 * - AND the requested page number > 0
					 * - AND the requested page size was bigger than the derived result limitation via the First/Top keyword.
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
		return this.queryMethod;
	}

	@FunctionalInterface
	private interface QueryExecution {
		Object execute(ReindexerParameterAccessor parameters, ReturnedType returnedType);
	}
}
