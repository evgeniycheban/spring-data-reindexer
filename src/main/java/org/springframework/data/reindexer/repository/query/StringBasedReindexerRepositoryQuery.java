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
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

/**
 * A string-based {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();

	private final NamedParameterPropertyAccessor propertyAccessor = new NamedParameterPropertyAccessor();

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
		StringBuilder result = new StringBuilder(query);
		char[] queryParts = query.toCharArray();
		int offset = 0;
		for (int i = 1; i < queryParts.length; i++) {
			char c = queryParts[i - 1];
			switch (c) {
				case '?' -> {
					int index = 0;
					int digits = 0;
					for (int j = i; j < queryParts.length; j++) {
						int digit = Character.digit(queryParts[j], 10);
						if (digit < 0) {
							break;
						}
						index *= 10;
						index += digit;
						digits++;
					}
					if (index < 1 || index > parameters.length) {
						throw new IllegalStateException("Invalid parameter reference at index: " + i);
					}
					String value = getParameterValuePart(parameters[index - 1]);
					result.replace(offset + i - 1, offset + i + digits, value);
					offset += value.length() - digits - 1;
					i += digits;
				}
				case ':' -> {
					if (queryParts[i] == '#') {
						int special = 1;
						StringBuilder sb = new StringBuilder();
						for (int j = i + 1; j < queryParts.length; j++) {
							if (queryParts[j] == '{') {
								special++;
								continue;
							}
							if (queryParts[j] == '}') {
								special++;
								break;
							}
							sb.append(queryParts[j]);
						}
						if (special != 3) {
							throw new IllegalStateException("Invalid SpEL expression provided at index: " + i);
						}
						Expression expression = this.spelExpressionParser.parseExpression(sb.toString());
						StandardEvaluationContext ctx = new StandardEvaluationContext(parameters);
						ctx.addPropertyAccessor(this.propertyAccessor);
						String value = getParameterValuePart(expression.getValue(ctx));
						result.replace(offset + i - 1, offset + i + expression.getExpressionString().length() + special, value);
						offset += value.length() - expression.getExpressionString().length() - special - 1;
						i += expression.getExpressionString().length() + special;
					}
					else {
						StringBuilder sb = new StringBuilder();
						for (int j = i; j < queryParts.length; j++) {
							if (Character.isWhitespace(queryParts[j])) {
								break;
							}
							sb.append(queryParts[j]);
						}
						String parameterName = sb.toString();
						Integer index = this.namedParameters.get(parameterName);
						Assert.notNull(index, () -> "No parameter found for name: " + parameterName);
						String value = getParameterValuePart(parameters[index]);
						result.replace(offset + i - 1, offset + i + parameterName.length(), value);
						offset += value.length() - parameterName.length() - 1;
						i += parameterName.length();
					}
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

	private final class NamedParameterPropertyAccessor implements PropertyAccessor {

		@Override
		public boolean canRead(EvaluationContext context, Object target, String name) {
			return StringBasedReindexerRepositoryQuery.this.namedParameters.containsKey(name);
		}

		@Override
		public TypedValue read(EvaluationContext context, Object target, String name) {
			Assert.state(target instanceof Object[], "target must be an array");
			Integer index = StringBasedReindexerRepositoryQuery.this.namedParameters.get(name);
			Assert.notNull(index, () -> "No parameter found for name: " + name);
			Object[] parameters = (Object[]) target;
			Object value = parameters[index];
			return new TypedValue(value);
		}

		@Override
		public boolean canWrite(EvaluationContext context, Object target, String name) {
			return false;
		}

		@Override
		public void write(EvaluationContext context, Object target, String name, Object newValue) {
			// NOOP
		}

		@Override
		public Class<?>[] getSpecificTargetClasses() {
			return new Class[] { Object.class.arrayType() };
		}

	}

}
