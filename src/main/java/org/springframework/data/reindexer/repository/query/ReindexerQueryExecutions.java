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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.ResultIterator;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerQueryExecutions {

	private ReindexerQueryExecutions() {
	}

	static Object toEntity(ResultIterator<?> iterator) {
		Object entity = null;
		try (iterator) {
			if (iterator.hasNext()) {
				entity = iterator.next();
			}
			if (iterator.hasNext()) {
				throw new IllegalStateException("Exactly row expected, but there are more");
			}
		}
		return entity;
	}

	static Stream<Object> toStream(ResultIterator<?> iterator) {
		Spliterator<Object> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false).onClose(iterator::close);
	}

	static List<Object> toList(ResultIterator<?> iterator) {
		List<Object> result = new ArrayList<>();
		try (iterator) {
			while (iterator.hasNext()) {
				Object next = iterator.next();
				if (next != null) {
					result.add(next);
				}
			}
		}
		return result;
	}
}
