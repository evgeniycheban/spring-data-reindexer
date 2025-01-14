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

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.Part.Type;
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

	private final ParameterAccessor parameters;

	private final ReturnedType returnedType;

	private final Map<String, ReindexerIndex> indexes;

	ReindexerQueryCreator(PartTree tree, Namespace<?> namespace,
			ReindexerEntityInformation<?, ?> entityInformation,
			Map<String, ReindexerIndex> indexes,
			ParameterAccessor parameters, ReturnedType returnedType) {
		super(tree, parameters);
		this.tree = tree;
		this.namespace = namespace;
		this.entityInformation = entityInformation;
		this.parameters = parameters;
		this.returnedType = returnedType;
		this.indexes = indexes;
	}

	ParameterAccessor getParameters() {
		return this.parameters;
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
			case IN -> where(base, indexName, Condition.SET, parameters);
			case NOT_IN -> where(base.not(), indexName, Condition.SET, parameters);
			case IS_NOT_NULL -> base.isNotNull(indexName);
			case IS_NULL -> base.isNull(indexName);
			case SIMPLE_PROPERTY -> where(base, indexName, Condition.EQ, parameters);
			case NEGATING_SIMPLE_PROPERTY -> where(base.not(), indexName, Condition.EQ, parameters);
			case BETWEEN -> base.where(indexName, Condition.RANGE, getParameterValues(indexName, parameters.next(), parameters.next()));
			case TRUE -> base.where(indexName, Condition.EQ, true);
			case FALSE -> base.where(indexName, Condition.EQ, false);
			case LIKE, NOT_LIKE, STARTING_WITH, ENDING_WITH, CONTAINING, NOT_CONTAINING -> {
				if (part.getProperty().getLeafProperty().isCollection()) {
					yield where(part.getType() == Type.NOT_CONTAINING ? base.not() : base, indexName, Condition.SET, parameters);
				}
				Object value = parameters.next();
				Assert.isInstanceOf(String.class, value, "Value of '" + part.getType() + "' expression must be String");
				String expression = switch (part.getType()) {
					case STARTING_WITH -> value + "%";
					case ENDING_WITH -> "%" + value;
					case CONTAINING, NOT_CONTAINING -> "%" + value + "%";
					default -> (String) value;
				};
				yield part.getType() == Type.NOT_LIKE || part.getType() == Type.NOT_CONTAINING
						? base.not().like(indexName, expression) : base.like(indexName, expression);
			}
			default -> throw new IllegalArgumentException("Unsupported keyword!");
		};
	}

	private Query<?> where(Query<?> base, String indexName, Condition condition, Iterator<Object> parameters) {
		Object value = getParameterValue(indexName, parameters.next());
		if (value instanceof Collection<?> values) {
			return base.where(indexName, condition, values);
		}
		return base.where(indexName, condition, value);
	}

	private Object[] getParameterValues(String indexName, Object... values) {
		Object[] result = new Object[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = getParameterValue(indexName, values[i]);
		}
		return result;
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
			String[] fields = this.returnedType.getInputProperties().toArray(String[]::new);
			if (this.tree.isDistinct()) {
				for (String field : fields) {
					criteria.aggregateDistinct(field);
				}
				criteria.aggregateFacet(fields);
			}
			else {
				criteria.select(fields);
			}
		}
		else if (this.tree.isExistsProjection()) {
			criteria.select(this.entityInformation.getIdFieldName());
		}
		Pageable pageable = this.parameters.getPageable();
		if (pageable.isPaged()) {
			criteria.limit(pageable.getPageSize()).offset(getOffsetAsInteger(pageable));
		}
		if (sort.isSorted()) {
			for (Order order : sort) {
				criteria.sort(order.getProperty(), order.isDescending());
			}
		}
		if (this.tree.getMaxResults() != null) {
			if (pageable.isPaged()) {
				/*
				 * In order to return the correct results, we have to adjust the first result offset to be returned if:
				 * - a Pageable parameter is present
				 * - AND the requested page number > 0
				 * - AND the requested page size was bigger than the derived result limitation via the First/Top keyword.
				 */
				int firstResult = getOffsetAsInteger(pageable);
				if (pageable.getPageSize() > this.tree.getMaxResults() && firstResult > 0) {
					criteria.offset(firstResult - (pageable.getPageSize() - this.tree.getMaxResults()));
				}
			}
			criteria.limit(this.tree.getMaxResults());
		}
		if (this.tree.isExistsProjection()) {
			criteria.limit(1);
		}
		return criteria;
	}

	static int getOffsetAsInteger(Pageable pageable) {
		if (pageable.getOffset() > Integer.MAX_VALUE) {
			throw new InvalidDataAccessApiUsageException("Page offset exceeds Integer.MAX_VALUE (" + Integer.MAX_VALUE + ")");
		}
		return Math.toIntExact(pageable.getOffset());
	}
}
