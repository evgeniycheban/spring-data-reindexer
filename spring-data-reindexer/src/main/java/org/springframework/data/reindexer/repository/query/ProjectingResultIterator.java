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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.AggregationResult;
import ru.rt.restream.reindexer.AggregationResult.Facet;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 * @param <M> the mapped type to use
 */
public final class ProjectingResultIterator<M> implements ReindexerResultAccessor<M> {

	private final ResultIterator<?> delegate;

	private final Class<M> mappedType;

	private final Class<?> domainType;

	private final @Nullable AggregationResult aggregationFacet;

	private final Map<String, Set<String>> distinctAggregationResults;

	private final ReindexerConverter reindexerConverter;

	private final long size;

	private int aggregationPosition;

	ProjectingResultIterator(Query<?> query, ReturnedType projectionType, ReindexerConverter reindexerConverter) {
		this(query.execute(), projectionType, reindexerConverter);
	}

	@SuppressWarnings("unchecked")
	ProjectingResultIterator(ResultIterator<?> delegate, ReturnedType projectionType,
			ReindexerConverter reindexerConverter) {
		this(delegate, (Class<M>) projectionType.getReturnedType(), projectionType.getDomainType(), reindexerConverter);
	}

	/**
	 * Creates an instance.
	 * @param delegate the {@link ResultIterator} to use
	 * @param mappedType the mapped type to use
	 * @param domainType the domain type to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public ProjectingResultIterator(ResultIterator<?> delegate, Class<M> mappedType, Class<?> domainType,
			ReindexerConverter reindexerConverter) {
		this.delegate = delegate;
		this.mappedType = mappedType;
		this.domainType = domainType;
		this.reindexerConverter = reindexerConverter;
		this.aggregationFacet = getAggregationFacet();
		this.distinctAggregationResults = getDistinctAggregationResults();
		this.size = this.aggregationFacet != null ? this.aggregationFacet.getFacets().size() : delegate.size();
	}

	@Override
	public @Nullable AggregationResult aggregationResult(String type, String field) {
		Assert.hasText(type, "type must not be empty");
		Assert.hasText(field, "field must not be empty");
		for (AggregationResult result : aggResults()) {
			if (type.equalsIgnoreCase(result.getType()) && result.getFields().contains(field)) {
				return result;
			}
		}
		return null;
	}

	@Override
	public long getTotalCount() {
		return this.delegate.getTotalCount();
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public List<AggregationResult> aggResults() {
		return this.delegate.aggResults();
	}

	@Override
	public float getCurrentRank() {
		return this.delegate.getCurrentRank();
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	@Override
	public boolean hasNext() {
		return this.delegate.hasNext()
				|| this.aggregationFacet != null && this.aggregationPosition < this.aggregationFacet.getFacets().size();
	}

	@Override
	public @Nullable M next() {
		return nextEntity();
	}

	private @Nullable M nextEntity() {
		if (this.aggregationFacet == null || this.distinctAggregationResults.isEmpty()) {
			Object entity = this.delegate.next();
			return project(entity);
		}
		int aggregationPosition = this.aggregationPosition++;
		List<String> fields = this.aggregationFacet.getFields();
		Map<String, Object> document = new HashMap<>(fields.size());
		for (int i = 0; i < fields.size(); i++) {
			String field = fields.get(i);
			Facet facet = this.aggregationFacet.getFacets().get(aggregationPosition);
			Set<String> distinctValues = this.distinctAggregationResults.get(field);
			if (i < facet.getValues().size() && distinctValues != null
					&& distinctValues.remove(facet.getValues().get(i))) {
				document.put(field, facet.getValues().get(i));
			}
			else {
				return null;
			}
		}
		return project(document);
	}

	private M project(Object entity) {
		EntityProjection<M, ?> descriptor = this.reindexerConverter.getProjectionIntrospector()
			.introspect(this.mappedType, this.domainType);
		return this.reindexerConverter.project(descriptor, entity);
	}

	private Map<String, Set<String>> getDistinctAggregationResults() {
		Map<String, Set<String>> result = new HashMap<>();
		for (AggregationResult aggregationResult : aggResults()) {
			if ("distinct".equals(aggregationResult.getType())) {
				Set<String> distincts = CollectionUtils.isEmpty(aggregationResult.getDistincts())
						? Collections.emptySet() : new HashSet<>(aggregationResult.getDistincts());
				result.put(aggregationResult.getFields().get(0), distincts);
			}
		}
		return result;
	}

	private @Nullable AggregationResult getAggregationFacet() {
		for (AggregationResult aggregationResult : aggResults()) {
			if ("facet".equals(aggregationResult.getType())) {
				if (aggregationResult.getFacets() == null) {
					aggregationResult.setFacets(Collections.emptyList());
				}
				return aggregationResult;
			}
		}
		return null;
	}

}
