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
import java.util.HashSet;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.CloseableIterator;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * A string-based {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

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
	}

	@Override
	public Object execute(Object[] parameters) {
		String query = String.format(this.queryMethod.getQuery(), parameters);
		if (this.queryMethod.isUpdateQuery()) {
			this.namespace.updateSql(query);
			return null;
		}
		if (this.queryMethod.isIteratorQuery()) {
			return this.namespace.execSql(query);
		}
		if (this.queryMethod.isStreamQuery()) {
			return toStream(this.namespace.execSql(query));
		}
		if (this.queryMethod.isListQuery()) {
			return toCollection(this.namespace.execSql(query), ArrayList::new);
		}
		if (this.queryMethod.isSetQuery()) {
			return toCollection(this.namespace.execSql(query), HashSet::new);
		}
		if (this.queryMethod.isOptionalQuery()) {
			return toOptionalEntity(this.namespace.execSql(query));
		}
		if (this.queryMethod.isQueryForEntity()) {
			return toEntity(this.namespace.execSql(query));
		}
		throw new IllegalStateException("Unsupported method return type " + this.queryMethod.getReturnedObjectType());
	}

	private <T> Stream<T> toStream(CloseableIterator<T> iterator) {
		Spliterator<T> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false);
	}

	private <T> Collection<T> toCollection(CloseableIterator<T> iterator, Supplier<Collection<T>> collectionSupplier) {
		Collection<T> result = collectionSupplier.get();
		try (CloseableIterator<T> it = iterator) {
			while (it.hasNext()) {
				result.add(it.next());
			}
		}
		return result;
	}

	public <T> Optional<T> toOptionalEntity(CloseableIterator<T> iterator) {
		T item = getOneInternal(iterator);
		return Optional.ofNullable(item);
	}

	public <T> T toEntity(CloseableIterator<T> iterator) {
		T item = getOneInternal(iterator);
		if (item == null) {
			throw new IllegalStateException("Exactly one item expected, but there is zero");
		}
		return item;
	}

	private <T> T getOneInternal(CloseableIterator<T> iterator) {
		try (CloseableIterator<T> it = iterator) {
			T item = null;
			if (it.hasNext()) {
				item = it.next();
			}
			if (it.hasNext()) {
				throw new IllegalStateException("Exactly one item expected, but there are more");
			}
			return item;
		}
	}

	@Override
	public QueryMethod getQueryMethod() {
		return this.queryMethod;
	}

}
