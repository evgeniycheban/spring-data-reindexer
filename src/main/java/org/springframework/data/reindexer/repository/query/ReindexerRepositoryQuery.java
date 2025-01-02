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
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.AggregationResult;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.model.PreferredConstructorDiscoverer;
import org.springframework.data.reindexer.repository.support.TransactionalNamespace;
import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
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
import org.springframework.data.support.PageableExecutionUtils;
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

	private final QueryPostProcessor queryPostProcessor;

	private final QueryExecution queryExecution;

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
		this.queryPostProcessor = new DelegatingQueryPostProcessor(QueryMethodPostProcessor.values());
		this.queryExecution = new DelegatingQueryExecution(QueryMethodExecution.values());
	}

	@Override
	public Object execute(Object[] parameters) {
		ReindexerQuery query = this.queryPostProcessor.postProcess(new ReindexerQuery(this.namespace.query()), this, parameters);
		return this.queryExecution.execute(query, this, parameters);
	}

	@Override
	public ReindexerQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	private interface QueryPostProcessor {
		ReindexerQuery postProcess(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters);
	}

	private static final class DelegatingQueryPostProcessor implements QueryPostProcessor {

		private final List<QueryPostProcessor> delegates;

		private DelegatingQueryPostProcessor(QueryPostProcessor... delegates) {
			this.delegates = List.of(delegates);
		}

		@Override
		public ReindexerQuery postProcess(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
			for (QueryPostProcessor delegate : this.delegates) {
				query = delegate.postProcess(query, repositoryQuery, parameters);
			}
			return query;
		}
	}

	private enum QueryMethodPostProcessor implements QueryPostProcessor {
		WHERE {
			@Override
			public ReindexerQuery postProcess(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				ParametersParameterAccessor accessor =
						new ParametersParameterAccessor(repositoryQuery.queryMethod.getParameters(), parameters);
				Iterator<Object> iterator = accessor.iterator();
				ReindexerQuery base = query;
				for (OrPart node : repositoryQuery.tree) {
					Iterator<Part> parts = node.iterator();
					Assert.state(parts.hasNext(), () -> "No part found in PartTree " + repositoryQuery.tree);
					ReindexerQuery criteria = where(repositoryQuery, parts.next(), base, iterator);
					while (parts.hasNext()) {
						criteria = where(repositoryQuery, parts.next(), criteria, iterator);
					}
					base = criteria.or();
				}
				return query;
			}

			private ReindexerQuery where(ReindexerRepositoryQuery repositoryQuery, Part part, ReindexerQuery criteria, Iterator<Object> parameters) {
				String indexName = part.getProperty().toDotPath();
				return switch (part.getType()) {
					case GREATER_THAN ->
							criteria.where(indexName, Query.Condition.GT, getParameterValue(repositoryQuery, indexName, parameters.next()));
					case GREATER_THAN_EQUAL ->
							criteria.where(indexName, Query.Condition.GE, getParameterValue(repositoryQuery, indexName, parameters.next()));
					case LESS_THAN ->
							criteria.where(indexName, Query.Condition.LT, getParameterValue(repositoryQuery, indexName, parameters.next()));
					case LESS_THAN_EQUAL ->
							criteria.where(indexName, Query.Condition.LE, getParameterValue(repositoryQuery, indexName, parameters.next()));
					case IN, CONTAINING -> createInQuery(repositoryQuery, criteria, indexName, parameters);
					case NOT_IN, NOT_CONTAINING ->
							createInQuery(repositoryQuery, criteria.not(), indexName, parameters);
					case IS_NOT_NULL -> criteria.isNotNull(indexName);
					case IS_NULL -> criteria.isNull(indexName);
					case SIMPLE_PROPERTY ->
							criteria.where(indexName, Query.Condition.EQ, getParameterValue(repositoryQuery, indexName, parameters.next()));
					case NEGATING_SIMPLE_PROPERTY ->
							criteria.not().where(indexName, Query.Condition.EQ, getParameterValue(repositoryQuery, indexName, parameters.next()));
					default -> throw new IllegalArgumentException("Unsupported keyword!");
				};
			}

			private ReindexerQuery createInQuery(ReindexerRepositoryQuery repositoryQuery, ReindexerQuery criteria, String indexName, Iterator<Object> parameters) {
				Object value = getParameterValue(repositoryQuery, indexName, parameters.next());
				Assert.isInstanceOf(Collection.class, value, () -> "Expected Collection but got " + value);
				return criteria.where(indexName, Query.Condition.SET, (Collection<?>) value);
			}

			private Object getParameterValue(ReindexerRepositoryQuery repositoryQuery, String indexName, Object value) {
				if (value instanceof Enum<?>) {
					ReindexerIndex index = repositoryQuery.indexes.get(indexName);
					Assert.notNull(index, () -> "Index not found: " + indexName);
					if (index.getFieldType() == FieldType.STRING) {
						return ((Enum<?>) value).name();
					}
					return ((Enum<?>) value).ordinal();
				}
				if (value instanceof Collection<?> values) {
					List<Object> result = new ArrayList<>(values.size());
					for (Object object : values) {
						result.add(getParameterValue(repositoryQuery, indexName, object));
					}
					return result;
				}
				if (value != null && value.getClass().isArray()) {
					int length = Array.getLength(value);
					List<Object> result = new ArrayList<>(length);
					for (int i = 0; i < length; i++) {
						result.add(getParameterValue(repositoryQuery, indexName, Array.get(value, i)));
					}
					return result;
				}
				return value;
			}
		},
		FROM {
			@Override
			public ReindexerQuery postProcess(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				if (repositoryQuery.queryMethod.getDomainClass() == repositoryQuery.queryMethod.getReturnedObjectType()) {
					return query;
				}
				Class<?> type = repositoryQuery.queryMethod.getParameters().hasDynamicProjection()
						? (Class<?>) parameters[repositoryQuery.queryMethod.getParameters().getDynamicProjectionIndex()]
						: repositoryQuery.queryMethod.getReturnedObjectType();
				ReturnedType projectionType = ReturnedType.of(type, repositoryQuery.queryMethod.getDomainClass(), repositoryQuery.queryMethod.getFactory());
				return query.from(projectionType);
			}
		},
		SORT {
			@Override
			public ReindexerQuery postProcess(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				if (!repositoryQuery.queryMethod.getParameters().hasSortParameter()) {
					return query;
				}
				Sort sort = (Sort) parameters[repositoryQuery.queryMethod.getParameters().getSortIndex()];
				return query.sort(sort);
			}
		},
		PAGEABLE {
			@Override
			public ReindexerQuery postProcess(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				if (!repositoryQuery.queryMethod.getParameters().hasPageableParameter()) {
					return query;
				}
				Pageable pageable = (Pageable) parameters[repositoryQuery.queryMethod.getParameters().getPageableIndex()];
				return query.pageable(pageable);
			}
		}
	}

	private interface QueryExecution {
		Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters);
	}

	private static final class DelegatingQueryExecution implements QueryExecution {

		private final List<QueryMethodExecution> executions;

		private DelegatingQueryExecution(QueryMethodExecution... executions) {
			this.executions = List.of(executions);
		}

		@Override
		public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
			for (QueryMethodExecution execution : this.executions) {
				if (execution.supports(repositoryQuery)) {
					return execution.execute(query, repositoryQuery, parameters);
				}
			}
			return fallbackToSingleResultQuery(query, repositoryQuery.queryMethod);
		}

		private Object fallbackToSingleResultQuery(ReindexerQuery query, ReindexerQueryMethod queryMethod) {
			Object entity = toEntity(query, queryMethod);
			if (queryMethod.isOptionalQuery()) {
				return Optional.ofNullable(entity);
			}
			Assert.state(entity != null, "Exactly one item expected, but there is zero");
			return entity;
		}

		private Object toEntity(ReindexerQuery query, ReindexerQueryMethod queryMethod) {
			try (ResultIterator<?> iterator = new ProjectingResultIterator(query, queryMethod)) {
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
	}

	private enum QueryMethodExecution implements QueryExecution {
		COLLECTION {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				try (ResultIterator<?> iterator = new ProjectingResultIterator(query, repositoryQuery.queryMethod)) {
					Collection<Object> result = CollectionFactory.createCollection(repositoryQuery.queryMethod.getReturnType(),
							repositoryQuery.queryMethod.getReturnedObjectType(), (int) iterator.size());
					while (iterator.hasNext()) {
						result.add(iterator.next());
					}
					return result;
				}
			}

			@Override
			public boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.queryMethod.isCollectionQuery() && !repositoryQuery.queryMethod.getParameters().hasPageableParameter();
			}
		},
		STREAM {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				ResultIterator<?> iterator = new ProjectingResultIterator(query, repositoryQuery.queryMethod);
				Spliterator<?> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
				return StreamSupport.stream(spliterator, false).onClose(iterator::close);
			}

			@Override
			public boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.queryMethod.isStreamQuery();
			}
		},
		ITERATOR {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				return new ProjectingResultIterator(query, repositoryQuery.queryMethod);
			}

			@Override
			public boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.queryMethod.isIteratorQuery();
			}
		},
		PAGEABLE {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				Pageable pageable = (Pageable) parameters[repositoryQuery.queryMethod.getParameters().getPageableIndex()];
				try (ProjectingResultIterator iterator = new ProjectingResultIterator(query, repositoryQuery.queryMethod)) {
					List<Object> content = new ArrayList<>();
					while (iterator.hasNext()) {
						content.add(iterator.next());
					}
					if (repositoryQuery.queryMethod.isPageQuery()) {
						return pageable.isPaged() ? PageableExecutionUtils.getPage(content, pageable, iterator::getTotalCount)
								: new PageImpl<>(content);
					}
					if (repositoryQuery.queryMethod.isListQuery()) {
						return content;
					}
					throw new IllegalStateException("Unsupported return type for Pageable query " + repositoryQuery.queryMethod.getReturnType());
				}
			}

			@Override
			public boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.queryMethod.getParameters().hasPageableParameter();
			}
		},
		COUNT {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				return query.count();
			}

			@Override
			boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.tree.isCountProjection();
			}
		},
		EXISTS {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				return query.exists();
			}

			@Override
			boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.tree.isExistsProjection();
			}
		},
		DELETE {
			@Override
			public Object execute(ReindexerQuery query, ReindexerRepositoryQuery repositoryQuery, Object[] parameters) {
				query.delete();
				return null;
			}

			@Override
			boolean supports(ReindexerRepositoryQuery repositoryQuery) {
				return repositoryQuery.tree.isDelete();
			}
		};
		abstract boolean supports(ReindexerRepositoryQuery repositoryQuery);
	}

	private static final class ProjectingResultIterator implements ResultIterator<Object> {

		private static final Map<Class<?>, Constructor<?>> cache = new ConcurrentHashMap<>();

		private final ResultIterator<?> delegate;

		private final ReindexerQueryMethod queryMethod;

		private final ReturnedType projectionType;

		private ProjectingResultIterator(ReindexerQuery query, ReindexerQueryMethod queryMethod) {
			this.delegate = query.execute();
			this.queryMethod = queryMethod;
			this.projectionType = query.projectionType;
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
					return this.queryMethod.getFactory().createProjection(this.projectionType.getReturnedType(), item);
				}
				List<String> properties = this.projectionType.getInputProperties();
				Object[] values = new Object[properties.size()];
				for (int i = 0; i < properties.size(); i++) {
					values[i] = BeanPropertyUtils.getProperty(item, properties.get(i));
				}
				Constructor<?> constructor = cache.computeIfAbsent(this.projectionType.getReturnedType(), (type) -> {
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

	private static final class ReindexerQuery {

		private Query<?> query;

		private ReturnedType projectionType;

		private ReindexerQuery(Query<?> query) {
			this.query = query;
		}

		private ReindexerQuery where(String indexName, Condition condition, Object... values) {
			this.query = this.query.where(indexName, condition, values);
			return this;
		}

		private ReindexerQuery where(String indexName, Condition condition, Collection<?> values) {
			this.query = this.query.where(indexName, condition, values);
			return this;
		}

		private ReindexerQuery not() {
			this.query = this.query.not();
			return this;
		}

		private ReindexerQuery or() {
			this.query = this.query.or();
			return this;
		}

		private ReindexerQuery isNull(String indexName) {
			this.query = this.query.isNull(indexName);
			return this;
		}

		private ReindexerQuery isNotNull(String indexName) {
			this.query = this.query.isNotNull(indexName);
			return this;
		}

		private ReindexerQuery sort(Sort sort) {
			for (Order order : sort) {
				this.query = this.query.sort(order.getProperty(), order.isDescending());
			}
			return this;
		}

		private ReindexerQuery pageable(Pageable pageable) {
			if (pageable.isPaged()) {
				for (Order order : pageable.getSort()) {
					this.query = this.query.sort(order.getProperty(), order.isDescending());
				}
				this.query = this.query.limit(pageable.getPageSize()).offset((int) pageable.getOffset()).reqTotal();
			}
			return this;
		}

		private ReindexerQuery from(ReturnedType projectionType) {
			this.projectionType = projectionType;
			if (projectionType.hasInputProperties()) {
				this.query = this.query.select(projectionType.getInputProperties().toArray(String[]::new));
			}
			return this;
		}

		private ResultIterator<?> execute() {
			return this.query.execute();
		}

		private void delete() {
			this.query.delete();
		}

		private boolean exists() {
			return this.query.exists();
		}

		private long count() {
			return this.query.count();
		}

	}

}
