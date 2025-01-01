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
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.AggregationResult;

import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.model.PreferredConstructorDiscoverer;
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
import org.springframework.data.repository.query.ReturnedType;
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

	private final Map<Class<?>, Constructor<?>> preferredConstructors = new ConcurrentHashMap<>();

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final PartTree tree;

	private final Map<String, ReindexerIndex> indexes;

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
	}

	@Override
	public Object execute(Object[] parameters) {
		ReturnedType projectionType = getProjectionType(parameters);
		Query<?> query = createQuery(projectionType, parameters);
		if (this.queryMethod.isCollectionQuery()) {
			return toCollection(query, projectionType);
		}
		if (this.queryMethod.isStreamQuery()) {
			return toStream(query, projectionType);
		}
		if (this.queryMethod.isIteratorQuery()) {
			return new ProjectingResultIterator(query, projectionType);
		}
		if (this.queryMethod.isQueryForEntity()) {
			Object entity = toEntity(query, projectionType);
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
		return Optional.ofNullable(toEntity(query, projectionType));
	}

	private ReturnedType getProjectionType(Object[] parameters) {
		if (this.queryMethod.getDomainClass() == this.queryMethod.getReturnedObjectType()) {
			return null;
		}
		Class<?> type = this.queryMethod.getParameters().hasDynamicProjection()
				? (Class<?>) parameters[this.queryMethod.getParameters().getDynamicProjectionIndex()]
				: this.queryMethod.getReturnedObjectType();
		return ReturnedType.of(type, this.queryMethod.getDomainClass(), this.queryMethod.getFactory());
	}

	private Query<?> createQuery(ReturnedType projectionType, Object[] parameters) {
		ParametersParameterAccessor accessor =
				new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		String[] selectFields = (projectionType != null) ? projectionType.getInputProperties().toArray(String[]::new)
				: new String[0];
		Query<?> base = this.namespace.query().select(selectFields);
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

	private Collection<?> toCollection(Query<?> query, ReturnedType projectionType) {
		try (ResultIterator<?> iterator = new ProjectingResultIterator(query, projectionType)) {
			Collection<Object> result = CollectionFactory.createCollection(this.queryMethod.getReturnType(),
					this.queryMethod.getReturnedObjectType(), (int) iterator.size());
			while (iterator.hasNext()) {
				result.add(iterator.next());
			}
			return result;
		}
	}

	private Stream<?> toStream(Query<?> query, ReturnedType projectionType) {
		ResultIterator<?> iterator = new ProjectingResultIterator(query, projectionType);
		Spliterator<?> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false).onClose(iterator::close);
	}

	private Object toEntity(Query<?> query, ReturnedType projectionType) {
		try (ResultIterator<?> iterator = new ProjectingResultIterator(query, projectionType)) {
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

		private final ResultIterator<?> delegate;

		private final ReturnedType projectionType;

		private ProjectingResultIterator(Query<?> query, ReturnedType projectionType) {
			this.delegate = query.execute();
			this.projectionType = projectionType;
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
			if (this.projectionType != null) {
				if (this.projectionType.getReturnedType().isInterface()) {
					return ReindexerRepositoryQuery.this.queryMethod.getFactory().createProjection(this.projectionType.getReturnedType(), item);
				}
				List<String> properties = this.projectionType.getInputProperties();
				Object[] values = new Object[properties.size()];
				for (int i = 0; i < properties.size(); i++) {
					values[i] = BeanPropertyUtils.getProperty(item, properties.get(i));
				}
				Constructor<?> constructor = ReindexerRepositoryQuery.this.preferredConstructors.computeIfAbsent(this.projectionType.getReturnedType(), (type) -> {
					PreferredConstructor<?, ?> preferredConstructor = PreferredConstructorDiscoverer.discover(type);
					Assert.state(preferredConstructor != null, () -> "No preferred constructor found for " + type);
					return preferredConstructor.getConstructor();
				});
				try {
					return constructor.newInstance(values);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			return item;
		}

	}

}
