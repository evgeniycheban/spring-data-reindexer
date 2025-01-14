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
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerQueryExecutions {

	private ReindexerQueryExecutions() {
	}

	static Object toEntity(Supplier<ResultIterator<?>> iteratorSupplier, ReindexerQueryMethod method) {
		Object entity = null;
		try (ResultIterator<?> iterator = iteratorSupplier.get()) {
			if (iterator.hasNext()) {
				entity = iterator.next();
			}
			if (iterator.hasNext()) {
				throw new IllegalStateException("Exactly row expected, but there are more");
			}
		}
		if (method.isOptionalQuery()) {
			return Optional.ofNullable(entity);
		}
		Assert.state(entity != null, "Exactly one item expected, but there is zero");
		return entity;
	}

	static Stream<Object> toStream(ResultIterator<?> iterator) {
		Spliterator<Object> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false).onClose(iterator::close);
	}

	static Page<Object> toPage(Supplier<ResultIterator<?>> iteratorSupplier, ParameterAccessor parameters) {
		try (ResultIterator<?> iterator = iteratorSupplier.get()) {
			return PageableExecutionUtils.getPage(toList(iterator), parameters.getPageable(), iterator::getTotalCount);
		}
	}

	static List<Object> toList(Supplier<ResultIterator<?>> iteratorSupplier) {
		try (ResultIterator<?> iterator = iteratorSupplier.get()) {
			return toList(iterator);
		}
	}

	private static List<Object> toList(ResultIterator<?> iterator) {
		List<Object> result = new ArrayList<>();
		while (iterator.hasNext()) {
			Object next = iterator.next();
			if (next != null) {
				result.add(next);
			}
		}
		return result;
	}
}
