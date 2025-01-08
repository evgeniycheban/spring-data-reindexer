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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter.QueryExpressionEvaluator;
import org.springframework.util.Assert;

/**
 * A string-based {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private static final String EXPRESSION_PARAMETER_PREFIX = "__$synthetic$__";

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final QueryExpressionEvaluator queryEvaluator;

	private final Map<String, Integer> namedParameters;

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
	}

	private QueryExpressionEvaluator createQueryExpressionEvaluator(QueryMethodValueEvaluationContextAccessor accessor) {
		ValueExpressionQueryRewriter queryRewriter = ValueExpressionQueryRewriter
				.of(ValueExpressionParser.create(), (index, expression) -> EXPRESSION_PARAMETER_PREFIX + index, String::concat);
		return queryRewriter.withEvaluationContextAccessor(accessor).parse(queryMethod.getQuery(), queryMethod.getParameters());
	}

	@Override
	public Object execute(Object[] parameters) {
		if (this.queryMethod.isUpdateQuery()) {
			this.namespace.updateSql(prepareQuery(parameters));
			return null;
		}
		if (this.queryMethod.isIteratorQuery()) {
			return this.namespace.execSql(prepareQuery(parameters));
		}
		if (this.queryMethod.isStreamQuery()) {
			return toStream(this.namespace.execSql(prepareQuery(parameters)));
		}
		if (this.queryMethod.isListQuery()) {
			return toCollection(this.namespace.execSql(prepareQuery(parameters)), ArrayList::new);
		}
		if (this.queryMethod.isSetQuery()) {
			return toCollection(this.namespace.execSql(prepareQuery(parameters)), HashSet::new);
		}
		if (this.queryMethod.isOptionalQuery()) {
			return toOptionalEntity(this.namespace.execSql(prepareQuery(parameters)));
		}
		if (this.queryMethod.isQueryForEntity()) {
			return toEntity(this.namespace.execSql(prepareQuery(parameters)));
		}
		throw new IllegalStateException("Unsupported method return type " + this.queryMethod.getReturnedObjectType());
	}

	private String prepareQuery(Object[] parameters) {
		Map<String, Object> parameterMap = this.queryEvaluator.evaluate(parameters);
		StringBuilder result = new StringBuilder(this.queryEvaluator.getQueryString());
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
							value = parameters[index];
						}
						else {
							int index;
							try {
								index = Integer.parseInt(parameterReference);
							}
							catch (NumberFormatException e) {
								throw new IllegalStateException("Invalid parameter reference: " + parameterReference + " at index: " + i);
							}
							value = parameters[index - 1];
						}
					}
					String valueString = getParameterValuePart(value);
					result.replace(offset + i - 1, offset + i + parameterReference.length(), valueString);
					offset += valueString.length() - parameterReference.length() - 1;
					i += parameterReference.length();
				}
			}
		}
		return result.toString();
	}

	private String getParameterValuePart(Object value) {
		if (value instanceof String) {
			return "'" + value + "'";
		}
		return String.valueOf(value);
	}

	private <T> Stream<T> toStream(ResultIterator<T> iterator) {
		Spliterator<T> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false);
	}

	private <T> Collection<T> toCollection(ResultIterator<T> iterator, Supplier<Collection<T>> collectionSupplier) {
		Collection<T> result = collectionSupplier.get();
		try (ResultIterator<T> it = iterator) {
			while (it.hasNext()) {
				result.add(it.next());
			}
		}
		return result;
	}

	private <T> Optional<T> toOptionalEntity(ResultIterator<T> iterator) {
		T item = getOneInternal(iterator);
		return Optional.ofNullable(item);
	}

	private <T> T toEntity(ResultIterator<T> iterator) {
		T item = getOneInternal(iterator);
		if (item == null) {
			throw new IllegalStateException("Exactly one item expected, but there is zero");
		}
		return item;
	}

	private <T> T getOneInternal(ResultIterator<T> iterator) {
		T item = null;
		try (ResultIterator<T> it = iterator) {
			if (it.hasNext()) {
				item = it.next();
			}
			if (it.hasNext()) {
				throw new IllegalStateException("Exactly one item expected, but there are more");
			}
		}
		return item;
	}

	@Override
	public QueryMethod getQueryMethod() {
		return this.queryMethod;
	}
}
