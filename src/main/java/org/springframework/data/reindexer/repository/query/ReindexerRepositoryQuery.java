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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.AggregationResult;
import org.springframework.data.reindexer.repository.support.TransactionalNamespace;
import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import org.springframework.core.CollectionFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.repository.query.parser.PartTree.OrPart;
import org.springframework.util.Assert;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final PartTree tree;

	private final Map<String, ReindexerIndex> indexes;

	private final String[] selectFields;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link ReindexerQueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use                         
	 */
	public ReindexerRepositoryQuery(ReindexerQueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		ReindexerNamespace<?> namespace = (ReindexerNamespace<?>) reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.namespace = new TransactionalNamespace<>(namespace);
		this.tree = new PartTree(queryMethod.getName(), entityInformation.getJavaType());
		this.indexes = new HashMap<>();
		for (ReindexerIndex index : namespace.getIndexes()) {
			this.indexes.put(index.getName(), index);
		}
		this.selectFields = getSelectFields();
	}

	private String[] getSelectFields() {
		if (this.queryMethod.getDomainClass() == this.queryMethod.getReturnedObjectType()
				|| this.queryMethod.getParameters().hasDynamicProjection()) {
			return new String[0];
		}
		return getSelectFields(this.queryMethod.getReturnedObjectType());
	}

	@Override
	public Object execute(Object[] parameters) {
		Query<?> query = createQuery(parameters);
		if (this.queryMethod.isCollectionQuery()) {
			return toCollection(query, parameters);
		}
		if (this.queryMethod.isStreamQuery()) {
			return toStream(query, parameters);
		}
		if (this.queryMethod.isIteratorQuery()) {
			return new ProjectingResultIterator(query, parameters);
		}
		if (this.queryMethod.isQueryForEntity()) {
			Object entity = toEntity(query, parameters);
			Assert.state(entity != null, "Exactly one item expected, but there is zero");
			return entity;
		}
		if (this.tree.isExistsProjection()) {
			return query.exists();
		}
		if (this.tree.isCountProjection()) {
			return query.count();
		}
		if (this.tree.isDelete()) {
			query.delete();
			return null;
		}
		return Optional.ofNullable(toEntity(query, parameters));
	}

	private Query<?> createQuery(Object[] parameters) {
		ParametersParameterAccessor accessor =
				new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		Query<?> base = this.namespace.query().select(getSelectFields(parameters));
		Iterator<Object> iterator = accessor.iterator();
		for (OrPart node : this.tree) {
			Iterator<Part> parts = node.iterator();
			Assert.state(parts.hasNext(), () -> "No part found in PartTree " + this.tree);
			Query<?> criteria = where(parts.next(), base, iterator);
			while (parts.hasNext()) {
				criteria = where(parts.next(), criteria, iterator);
			}
			base = criteria.or();
		}
		if (this.queryMethod.getParameters().hasSortParameter()) {
			Sort sort = (Sort) parameters[this.queryMethod.getParameters().getSortIndex()];
			for (Order order : sort) {
				base = base.sort(order.getProperty(), order.isDescending());
			}
		}
		return base;
	}

	private String[] getSelectFields(Object[] parameters) {
		if (this.queryMethod.getParameters().hasDynamicProjection()) {
			Class<?> type = (Class<?>) parameters[this.queryMethod.getParameters().getDynamicProjectionIndex()];
			return getSelectFields(type);
		}
		return this.selectFields;
	}

	private String[] getSelectFields(Class<?> type) {
		if (type.isInterface()) {
			List<PropertyDescriptor> inputProperties = this.queryMethod.getFactory()
					.getProjectionInformation(type).getInputProperties();
			String[] result = new String[inputProperties.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = inputProperties.get(i).getName();
			}
			return result;
		}
		else {
			List<Field> inheritedFields = BeanPropertyUtils.getInheritedFields(type);
			String[] result = new String[inheritedFields.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = inheritedFields.get(i).getName();
			}
			return result;
		}
	}

	private Query<?> where(Part part, Query<?> criteria, Iterator<Object> parameters) {
		String indexName = part.getProperty().toDotPath();
		switch (part.getType()) {
			case GREATER_THAN:
				return criteria.where(indexName, Query.Condition.GT, getParameterValue(indexName, parameters.next()));
			case GREATER_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.GE, getParameterValue(indexName, parameters.next()));
			case LESS_THAN:
				return criteria.where(indexName, Query.Condition.LT, getParameterValue(indexName, parameters.next()));
			case LESS_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.LE, getParameterValue(indexName, parameters.next()));
			case IN:
			case CONTAINING:
				return createInQuery(criteria, indexName, parameters);
			case NOT_IN:
			case NOT_CONTAINING:
				return createInQuery(criteria.not(), indexName, parameters);
			case IS_NOT_NULL:
				return criteria.isNotNull(indexName);
			case IS_NULL:
				return criteria.isNull(indexName);
			case SIMPLE_PROPERTY:
				return criteria.where(indexName, Query.Condition.EQ, getParameterValue(indexName, parameters.next()));
			case NEGATING_SIMPLE_PROPERTY:
				return criteria.not().where(indexName, Query.Condition.EQ, getParameterValue(indexName, parameters.next()));
			default:
				throw new IllegalArgumentException("Unsupported keyword!");
		}
	}

    private Query<?> createInQuery(Query<?> criteria, String indexName, Iterator<Object> parameters) {
        Object value = getParameterValue(indexName, parameters.next());
        Assert.isInstanceOf(Collection.class, value, () -> "Expected Collection but got " + value);
        return criteria.where(indexName, Query.Condition.SET, (Collection<?>) value);
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

	private Collection<?> toCollection(Query<?> query, Object[] parameters) {
		try (ResultIterator<?> iterator = new ProjectingResultIterator(query, parameters)) {
			Collection<Object> result = CollectionFactory.createCollection(this.queryMethod.getReturnType(),
					this.queryMethod.getReturnedObjectType(), (int) iterator.size());
			while (iterator.hasNext()) {
				result.add(iterator.next());
			}
			return result;
		}
	}

	private Stream<?> toStream(Query<?> query, Object[] parameters) {
		ResultIterator<?> iterator = new ProjectingResultIterator(query, parameters);
		Spliterator<?> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false).onClose(iterator::close);
	}

	private Object toEntity(Query<?> query, Object[] parameters) {
		try (ResultIterator<?> iterator = new ProjectingResultIterator(query, parameters)) {
			Object item = null;
			if (iterator.hasNext()) {
				item = iterator.next();
			}
			if (iterator.hasNext()) {
				throw new IllegalStateException("Exactly one item expected, but there are more");
			}
			return item;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Override
	public ReindexerQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	private final class ProjectingResultIterator implements ResultIterator<Object> {

		private final Object[] parameters;

		private final ResultIterator<?> delegate;

		private ProjectingResultIterator(Query<?> query, Object[] parameters) {
			this.parameters = parameters;
			this.delegate = query.execute(determineReturnType());
		}

		private Class<?> determineReturnType() {
			if (ReindexerRepositoryQuery.this.queryMethod.getParameters().hasDynamicProjection()) {
				Class<?> type = (Class<?>) this.parameters[ReindexerRepositoryQuery.this.queryMethod.getParameters().getDynamicProjectionIndex()];
				if (type.isInterface()) {
					return ReindexerRepositoryQuery.this.queryMethod.getDomainClass();
				}
				return type;
			}
			if (ReindexerRepositoryQuery.this.queryMethod.getDomainClass() != ReindexerRepositoryQuery.this.queryMethod.getReturnedObjectType()
					&& ReindexerRepositoryQuery.this.queryMethod.getReturnedObjectType().isInterface()) {
				return ReindexerRepositoryQuery.this.queryMethod.getDomainClass();
			}
			return ReindexerRepositoryQuery.this.queryMethod.getReturnedObjectType();
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
		public void close() {
			this.delegate.close();
		}

		@Override
		public boolean hasNext() {
			return this.delegate.hasNext();
		}

		@Override
		public Object next() {
			Object item = this.delegate.next();
			if (ReindexerRepositoryQuery.this.queryMethod.getParameters().hasDynamicProjection()) {
				Class<?> type = (Class<?>) this.parameters[ReindexerRepositoryQuery.this.queryMethod.getParameters().getDynamicProjectionIndex()];
				if (type.isInterface()) {
					return ReindexerRepositoryQuery.this.queryMethod.getFactory().createProjection(type, item);
				}
			}
			else if (ReindexerRepositoryQuery.this.queryMethod.getReturnedObjectType().isInterface()) {
				return ReindexerRepositoryQuery.this.queryMethod.getFactory()
						.createProjection(ReindexerRepositoryQuery.this.queryMethod.getReturnedObjectType(), item);
			}
			return item;
		}

	}

}
