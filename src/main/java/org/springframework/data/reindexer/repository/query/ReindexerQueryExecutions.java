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
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.core.CollectionFactory;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerQueryExecutions {

	/**
	 * Returns exactly one entity from the given {@link ResultIterator}.
	 * @param iterator the {@link ResultIterator} to use
	 * @param <E> the entity type to use
	 * @return the entity from the iterator to use, can be {@literal null}
	 * @throws IncorrectResultSizeDataAccessException if the iterator contains more than
	 * one entity
	 */
	public static <E> E toEntity(ResultIterator<E> iterator) {
		E entity = null;
		try (iterator) {
			if (iterator.hasNext()) {
				entity = iterator.next();
			}
			if (iterator.hasNext()) {
				throw new IncorrectResultSizeDataAccessException(1);
			}
		}
		return entity;
	}

	/**
	 * Produces a {@link Stream} of entities from the given {@link ResultIterator}.
	 * @param iterator the {@link ResultIterator} to use
	 * @param <E> the entity type to use
	 * @return the {@link Stream} of entities to use
	 */
	public static <E> Stream<E> toStream(ResultIterator<E> iterator) {
		Spliterator<E> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false).onClose(iterator::close);
	}

	/**
	 * Produces a {@link List} of entities from the given {@link ResultIterator}.
	 * @param iterator the {@link ResultIterator} to use
	 * @param <E> the entity type to use
	 * @return the {@link List} of entities to use
	 */
	public static <E> List<E> toList(ResultIterator<E> iterator) {
		List<E> result = new ArrayList<>();
		try (iterator) {
			while (iterator.hasNext()) {
				E next = iterator.next();
				if (next != null) {
					result.add(next);
				}
			}
		}
		return result;
	}

	/**
	 * Produces a {@link Collection} of entities from the given {@link ResultIterator}.
	 * @param iterator the {@link ResultIterator} to use
	 * @param collectionType the collection type to use
	 * @param <E> the entity type to use
	 * @return the {@link Collection} of entities to use
	 */
	public static <E> Collection<E> toCollection(ResultIterator<E> iterator, Class<?> collectionType) {
		Collection<E> result = CollectionFactory.createCollection(collectionType, Math.toIntExact(iterator.size()));
		try (iterator) {
			while (iterator.hasNext()) {
				E next = iterator.next();
				if (next != null) {
					result.add(next);
				}
			}
		}
		return result;
	}

	private ReindexerQueryExecutions() {
	}

}
