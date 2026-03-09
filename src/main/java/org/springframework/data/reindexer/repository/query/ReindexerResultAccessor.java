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

import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.AggregationResult;
import ru.rt.restream.reindexer.ResultIterator;

/**
 * Extends the {@link ResultIterator} to provide an additional methods to access the
 * Reindexer query result e.g., {@link #aggregationResult(String, String)}.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public interface ReindexerResultAccessor<E> extends ResultIterator<E> {

	/**
	 * Returns a {@literal double} value of the {@link AggregationResult} for the given
	 * {@code type} and {@code field}. Defaults to {@literal 0.0} if no
	 * {@code AggregationResult} found.
	 * @param type the aggregation type e.g., min, max, sum, avg
	 * @param field the field an aggregation function being called for
	 * @return the {@code AggregationResult}'s value to use
	 */
	default double aggregationValue(String type, String field) {
		AggregationResult result = aggregationResult(type, field);
		return result != null ? result.getValue() : 0.0d;
	}

	/**
	 * Returns an {@link AggregationResult} for the given {@code type} and {@code field}.
	 * @param type the aggregation type e.g., min, max, sum, avg
	 * @param field the field an aggregation function being called for
	 * @return the {@code AggregationResult} to use
	 */
	@Nullable AggregationResult aggregationResult(String type, String field);

}
