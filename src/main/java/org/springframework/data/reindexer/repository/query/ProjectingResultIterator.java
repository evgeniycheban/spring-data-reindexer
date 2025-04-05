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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.rt.restream.reindexer.AggregationResult;
import ru.rt.restream.reindexer.AggregationResult.Facet;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import org.springframework.core.convert.ConversionService;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.repository.query.ReturnedType;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ProjectingResultIterator implements ResultIterator<Object> {

	private final ResultIterator<?> delegate;

	private final ReturnedType projectionType;

	private final AggregationResult aggregationFacet;

	private final Map<String, Set<String>> distinctAggregationResults;

	private final ReindexerConverter reindexerConverter;

	private final ConversionService conversionService;

	private final boolean distinct;

	private int aggregationPosition;

	ProjectingResultIterator(Query<?> query, ReturnedType projectionType, ReindexerConverter reindexerConverter) {
		this(query.execute(), projectionType, reindexerConverter);
	}

	ProjectingResultIterator(ResultIterator<?> delegate, ReturnedType projectionType,
			ReindexerConverter reindexerConverter) {
		this.delegate = delegate;
		this.projectionType = projectionType;
		this.reindexerConverter = reindexerConverter;
		this.conversionService = reindexerConverter.getConversionService();
		this.aggregationFacet = getAggregationFacet();
		this.distinctAggregationResults = getDistinctAggregationResults();
		this.distinct = this.aggregationFacet != null && !this.distinctAggregationResults.isEmpty();
	}

	@Override
	public long getTotalCount() {
		return this.delegate.getTotalCount();
	}

	@Override
	public long size() {
		return this.delegate.size();
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

	@SuppressWarnings("unchecked")
	@Override
	public Object next() {
		Object entity = nextEntity();
		if (entity == null) {
			return null;
		}
		EntityProjection<Object, Object> descriptor = (EntityProjection<Object, Object>) this.reindexerConverter
			.getProjectionIntrospector()
			.introspect(this.projectionType.getReturnedType(), this.projectionType.getDomainType());
		return this.reindexerConverter.project(descriptor, entity);
	}

	private Object nextEntity() {
		if (!this.distinct) {
			return this.delegate.next();
		}
		Object entity;
		try {
			entity = this.projectionType.getDomainType().getDeclaredConstructor().newInstance();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		ReindexerMappingContext mappingContext = this.reindexerConverter.getMappingContext();
		ReindexerPersistentEntity<?> persistentEntity = mappingContext
			.getRequiredPersistentEntity(this.projectionType.getDomainType());
		int aggregationPosition = this.aggregationPosition++;
		List<String> fields = this.aggregationFacet.getFields();
		for (int i = 0; i < fields.size(); i++) {
			String field = fields.get(i);
			Facet facet = this.aggregationFacet.getFacets().get(aggregationPosition);
			if (i < facet.getValues().size()
					&& this.distinctAggregationResults.get(field).remove(facet.getValues().get(i))) {
				ReindexerPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty(field);
				Object value = this.conversionService.convert(facet.getValues().get(i), persistentProperty.getType());
				BeanPropertyUtils.setProperty(entity, field, value);
			}
			else {
				return null;
			}
		}
		return entity;
	}

	private Map<String, Set<String>> getDistinctAggregationResults() {
		Map<String, Set<String>> result = new HashMap<>();
		for (AggregationResult aggregationResult : aggResults()) {
			if ("distinct".equals(aggregationResult.getType())) {
				result.put(aggregationResult.getFields().get(0), new HashSet<>(aggregationResult.getDistincts()));
			}
		}
		return result;
	}

	private AggregationResult getAggregationFacet() {
		for (AggregationResult aggregationResult : aggResults()) {
			if ("facet".equals(aggregationResult.getType())) {
				return aggregationResult;
			}
		}
		return null;
	}

}
