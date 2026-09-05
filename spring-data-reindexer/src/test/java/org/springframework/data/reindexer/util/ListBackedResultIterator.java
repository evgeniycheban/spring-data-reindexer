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
package org.springframework.data.reindexer.util;

import java.util.List;

import ru.rt.restream.reindexer.AggregationResult;
import ru.rt.restream.reindexer.ResultIterator;

/**
 * A {@link ResultIterator} backed by a {@link List} for testing purposes.
 *
 * @author Evgeniy Cheban
 */
public class ListBackedResultIterator<T> implements ResultIterator<T> {

	private final List<T> list;

	private int position = 0;

	@SafeVarargs
	public ListBackedResultIterator(T... entities) {
		this.list = List.of(entities);
	}

	@Override
	public boolean hasNext() {
		return this.position < this.list.size();
	}

	@Override
	public T next() {
		return this.list.get(this.position++);
	}

	@Override
	public long getTotalCount() {
		return this.list.size();
	}

	@Override
	public long size() {
		return this.list.size();
	}

	@Override
	public List<AggregationResult> aggResults() {
		return List.of();
	}

	@Override
	public float getCurrentRank() {
		return 0;
	}

	@Override
	public void close() {
	}

}
