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

import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.Assert;

/**
 * A string-based {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final Map<String, Integer> namedParameters;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link QueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use
	 */
	public StringBasedReindexerRepositoryQuery(ReindexerQueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		this.namespace = reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.namedParameters = new HashMap<>();
		for (Parameter parameter : queryMethod.getParameters()) {
			if (parameter.isNamedParameter()) {
				parameter.getName().ifPresent(name -> this.namedParameters.put(name, parameter.getIndex()));
			}
		}
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
		String query = this.queryMethod.getQuery();
		StringBuilder sb = new StringBuilder(query);
		char[] queryParts = query.toCharArray();
		int offset = 0;
		for (int i = 1; i < queryParts.length; i++) {
			char c = queryParts[i - 1];
			switch (c) {
				case '?': {
					int j = i;
					int index = 0;
					int digits = 0;
					while (j < queryParts.length) {
						if (!Character.isDigit(queryParts[j])) {
							break;
						}
						index *= 10;
						index += Character.getNumericValue(queryParts[j++]);
						digits++;
					}
					String value = getParameterValuePart(parameters, index - 1);
					sb.replace(offset + i - 1, offset + i + digits, value);
					offset += value.length() - digits - 1;
					break;
				}
				case ':': {
					String parameterName = getParameterName(queryParts, i);
					Integer index = this.namedParameters.get(parameterName);
					Assert.notNull(index, () -> "No parameter found for name: " + parameterName);
					String value = getParameterValuePart(parameters, index);
					sb.replace(offset + i - 1, offset + i + parameterName.length(), value);
					offset += value.length() - parameterName.length() - 1;
					break;
				}
			}
		}
		return sb.toString();
	}

	private String getParameterName(char[] queryParts, int i) {
		StringBuilder sb = new StringBuilder();
		while (i < queryParts.length) {
			if (Character.isWhitespace(queryParts[i])) {
				break;
			}
			sb.append(queryParts[i++]);
		}
		return sb.toString();
	}

	private String getParameterValuePart(Object[] parameters, int index) {
		Assert.state(index >= 0 && index < parameters.length, () -> "No parameter found at index: " + index);
		Object value = parameters[index];
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
