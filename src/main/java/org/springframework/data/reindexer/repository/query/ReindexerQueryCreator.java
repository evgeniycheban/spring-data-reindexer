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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;

import org.springframework.data.core.PropertyPath;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.util.PageableUtils;
import org.springframework.data.reindexer.repository.util.QueryUtils;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.Part.IgnoreCaseType;
import org.springframework.data.repository.query.parser.Part.Type;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
final class ReindexerQueryCreator extends AbstractQueryCreator<Query<?>, Query<?>> {

	private final PartTree tree;

	private final Reindexer reindexer;

	private final Namespace<?> namespace;

	private final ReindexerEntityInformation<?, ?> entityInformation;

	private final ReindexerMappingContext mappingContext;

	private final ParameterAccessor parameters;

	private final ReturnedType returnedType;

	private final QueryParameterMapper queryParameterMapper;

	private final ReindexerQueryMethod method;

	private Query<?> base;

	ReindexerQueryCreator(PartTree tree, Reindexer reindexer, Namespace<?> namespace,
			ReindexerEntityInformation<?, ?> entityInformation, ReindexerMappingContext mappingContext,
			Map<String, ReindexerIndex> indexes, ReindexerConverter reindexerConverter, ParameterAccessor parameters,
			ReturnedType returnedType, ReindexerQueryMethod method) {
		super(tree, parameters);
		this.tree = tree;
		this.reindexer = reindexer;
		this.namespace = namespace;
		this.entityInformation = entityInformation;
		this.mappingContext = mappingContext;
		this.parameters = parameters;
		this.returnedType = returnedType;
		this.queryParameterMapper = new QueryParameterMapper(entityInformation.getJavaType(), indexes, mappingContext,
				reindexerConverter);
		this.method = method;
	}

	ParameterAccessor getParameters() {
		return this.parameters;
	}

	ReturnedType getReturnedType() {
		return this.returnedType;
	}

	@Override
	protected Query<?> create(Part part, Iterator<Object> parameters) {
		if (this.base == null) {
			this.base = this.namespace.query();
		}
		else {
			/*
			 * If base query already exists, this is the next PartTree.OrPart iteration
			 * and the OR operator is applied. Note that we need to open bracket to ensure
			 * correct handling of certain OR conditions.
			 *
			 * For example, in `findByNameOrValueNot`, the NOT part must be wrapped in
			 * brackets to produce correct results.
			 */
			this.base.or().openBracket();
		}
		return and(part, this.base, parameters);
	}

	@Override
	protected Query<?> and(Part part, Query<?> base, Iterator<Object> parameters) {
		String indexName = part.getProperty().toDotPath();
		return switch (part.getType()) {
			case GREATER_THAN, AFTER -> where(base, indexName, Condition.GT, parameters);
			case GREATER_THAN_EQUAL -> where(base, indexName, Condition.GE, parameters);
			case LESS_THAN, BEFORE -> where(base, indexName, Condition.LT, parameters);
			case LESS_THAN_EQUAL -> where(base, indexName, Condition.LE, parameters);
			case IN -> where(base, indexName, Condition.SET, parameters);
			case NOT_IN -> where(base.not(), indexName, Condition.SET, parameters);
			case IS_NOT_NULL -> base.isNotNull(indexName);
			case IS_NULL -> base.isNull(indexName);
			case NEGATING_SIMPLE_PROPERTY, SIMPLE_PROPERTY -> {
				boolean isSimpleComparison = switch (part.shouldIgnoreCase()) {
					case NEVER -> true;
					case WHEN_POSSIBLE -> part.getProperty().getType() != String.class;
					case ALWAYS -> false;
				};
				if (isSimpleComparison) {
					yield where(part.getType() == Type.NEGATING_SIMPLE_PROPERTY ? base.not() : base, indexName,
							Condition.EQ, parameters);
				}
				PropertyPath path = part.getProperty().getLeafProperty();
				if (part.shouldIgnoreCase() == IgnoreCaseType.ALWAYS) {
					Assert.isTrue(part.getProperty().getType() == String.class,
							() -> "Property '" + indexName + "' must be of type String but was " + path.getType());
				}
				Object value = parameters.next();
				Assert.notNull(value,
						() -> "Argument for creating like pattern for property '" + indexName + "' must not be null");
				yield part.getType() == Type.NEGATING_SIMPLE_PROPERTY ? base.not().like(indexName, value.toString())
						: base.like(indexName, value.toString());
			}
			case BETWEEN -> base.where(indexName, Condition.RANGE,
					this.queryParameterMapper.mapParameterValues(indexName, parameters.next(), parameters.next()));
			case TRUE -> base.where(indexName, Condition.EQ, true);
			case FALSE -> base.where(indexName, Condition.EQ, false);
			case LIKE, NOT_LIKE, STARTING_WITH, ENDING_WITH, CONTAINING, NOT_CONTAINING -> {
				if (part.getProperty().getLeafProperty().isCollection()) {
					yield where(part.getType() == Type.NOT_CONTAINING ? base.not() : base, indexName, Condition.SET,
							parameters);
				}
				Object value = parameters.next();
				Assert.isInstanceOf(String.class, value,
						() -> "Value of '" + part.getType() + "' expression must be String");
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
		Object value = this.queryParameterMapper.mapParameterValue(indexName, parameters.next());
		if (value instanceof Collection<?> values) {
			return base.where(indexName, condition, values);
		}
		return base.where(indexName, condition, value);
	}

	@Override
	protected Query<?> or(Query<?> base, Query<?> criteria) {
		// Close the bracket opened in this PartTree.OrPart iteration.
		return base.closeBracket();
	}

	@Override
	protected Query<?> complete(Query<?> criteria, Sort sort) {
		if (criteria == null) {
			criteria = this.namespace.query();
		}
		if (this.returnedType.needsCustomConstruction()) {
			String[] fields = QueryUtils.getSelectFields(this.mappingContext, this.returnedType, this.tree.isDistinct())
				.toArray(String[]::new);
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
			int limit = this.method.isSliceQuery() ? pageable.getPageSize() + 1 : pageable.getPageSize();
			criteria.limit(limit).offset(PageableUtils.getOffsetAsInteger(pageable));
		}
		if (sort.isSorted()) {
			for (Order order : sort) {
				criteria.sort(order.getProperty(), order.isDescending());
			}
		}
		if (this.tree.getMaxResults() != null) {
			if (pageable.isPaged()) {
				/*
				 * In order to return the correct results, we have to adjust the first
				 * result offset to be returned if: - a Pageable parameter is present -
				 * AND the requested page number > 0 - AND the requested page size was
				 * bigger than the derived result limitation via the First/Top keyword.
				 */
				int firstResult = PageableUtils.getOffsetAsInteger(pageable);
				if (pageable.getPageSize() > this.tree.getMaxResults() && firstResult > 0) {
					criteria.offset(firstResult - (pageable.getPageSize() - this.tree.getMaxResults()));
				}
			}
			criteria.limit(this.tree.getMaxResults());
		}
		if (this.tree.isExistsProjection()) {
			criteria.limit(1);
		}
		return QueryUtils.withJoins(criteria, this.returnedType.getDomainType(), this.mappingContext, this.reindexer);
	}

}
