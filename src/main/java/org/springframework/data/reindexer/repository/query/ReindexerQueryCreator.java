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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.ReindexerIndex;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerQueryCreator extends AbstractQueryCreator<Query<?>, Query<?>> {

	private final PartTree tree;

	private final Namespace<?> namespace;

	private final ReindexerEntityInformation<?, ?> entityInformation;

	private final ReindexerQueryMethod queryMethod;

	private final ParameterAccessor parameters;

	private final ReturnedType returnedType;

	private final Map<String, ReindexerIndex> indexes;

	ReindexerQueryCreator(PartTree tree, Namespace<?> namespace,
			ReindexerEntityInformation<?, ?> entityInformation,
			ReindexerQueryMethod queryMethod, Map<String, ReindexerIndex> indexes,
			ParameterAccessor parameters, ReturnedType returnedType) {
		super(tree, parameters);
		this.tree = tree;
		this.namespace = namespace;
		this.entityInformation = entityInformation;
		this.queryMethod = queryMethod;
		this.parameters = parameters;
		this.returnedType = returnedType;
		this.indexes = indexes;
	}

	ParameterAccessor getParameters() {
		return this.parameters;
	}

	ReindexerQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	ReturnedType getReturnedType() {
		return this.returnedType;
	}

	@Override
	protected Query<?> create(Part part, Iterator<Object> parameters) {
		return and(part, this.namespace.query(), parameters);
	}

	@Override
	protected Query<?> and(Part part, Query<?> base, Iterator<Object> parameters) {
		String indexName = part.getProperty().toDotPath();
		return switch (part.getType()) {
			case GREATER_THAN -> where(base, indexName, Condition.GT, parameters);
			case GREATER_THAN_EQUAL -> where(base, indexName, Condition.GE, parameters);
			case LESS_THAN -> where(base, indexName, Condition.LT, parameters);
			case LESS_THAN_EQUAL -> where(base, indexName, Condition.LE, parameters);
			case IN, CONTAINING -> where(base, indexName, Condition.SET, parameters);
			case NOT_IN, NOT_CONTAINING -> where(base.not(), indexName, Condition.SET, parameters);
			case IS_NOT_NULL -> base.not().isNull(indexName);
			case IS_NULL -> base.isNull(indexName);
			case SIMPLE_PROPERTY -> where(base, indexName, Condition.EQ, parameters);
			case NEGATING_SIMPLE_PROPERTY -> base.not().where(indexName, Condition.EQ, parameters);
			default -> throw new IllegalArgumentException("Unsupported keyword!");
		};
	}

	private Query<?> where(Query<?> base, String indexName, Condition condition, Iterator<Object> parameters) {
		Object value = getParameterValue(indexName, parameters.next());
		if (value instanceof Collection<?> values) {
			return base.where(indexName, condition, values);
		}
		else {
			return base.where(indexName, condition, value);
		}
	}

	private Object getParameterValue(String indexName, Object value) {
		if (value instanceof Enum<?>) {
			ReindexerIndex index = this.indexes.get(indexName);
			Assert.notNull(index, () -> "Index not found: " + indexName);
			if (index.getFieldType() == FieldType.STRING) {
				return ((Enum<?>) value).name();
			}
			return ((Enum<?>) value).ordinal();
		}
		if (value instanceof Collection<?> values) {
			List<Object> result = new ArrayList<>(values.size());
			for (Object object : values) {
				result.add(getParameterValue(indexName, object));
			}
			return result;
		}
		if (value != null && value.getClass().isArray()) {
			int length = Array.getLength(value);
			List<Object> result = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				result.add(getParameterValue(indexName, Array.get(value, i)));
			}
			return result;
		}
		return value;
	}

	@Override
	protected Query<?> or(Query<?> base, Query<?> criteria) {
		return criteria.or();
	}

	@Override
	protected Query<?> complete(Query<?> criteria, Sort sort) {
		if (criteria == null) {
			criteria = this.namespace.query();
		}
		if (this.returnedType.needsCustomConstruction()) {
			criteria = criteria.select(this.returnedType.getInputProperties().toArray(String[]::new));
		}
		else if (this.tree.isExistsProjection()) {
			criteria = criteria.select(this.entityInformation.getIdFieldName()).limit(1);
		}
		Pageable pageable = this.parameters.getPageable();
		if (pageable.isPaged()) {
			criteria.limit(pageable.getPageSize()).offset((int) pageable.getOffset());
			if (this.queryMethod.isPageQuery()) {
				criteria = criteria.reqTotal();
			}
		}
		if (sort.isSorted()) {
			for (Order order : sort) {
				criteria = criteria.sort(order.getProperty(), order.isDescending());
			}
		}
		if (this.tree.getMaxResults() != null) {
			criteria = criteria.limit(this.tree.getMaxResults());
		}
		if (this.tree.isExistsProjection()) {
			criteria.limit(1);
		}
		return criteria;
	}
}
