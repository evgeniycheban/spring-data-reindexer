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
import org.springframework.data.repository.query.ParameterAccessor;
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
		ParameterAccessor parameterAccessor = new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		ReindexerQuery reindexerQuery = new ReindexerQuery(this);
		this.queryPostProcessor.postProcess(reindexerQuery, parameterAccessor);
		return this.queryExecution.execute(reindexerQuery, parameterAccessor);
	}

	@Override
	public ReindexerQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	private interface QueryPostProcessor {
		void postProcess(ReindexerQuery query, ParameterAccessor parameterAccessor);
	}

	private static final class DelegatingQueryPostProcessor implements QueryPostProcessor {

		private final List<QueryPostProcessor> delegates;

		private DelegatingQueryPostProcessor(QueryPostProcessor... delegates) {
			this.delegates = List.of(delegates);
		}

		@Override
		public void postProcess(ReindexerQuery query, ParameterAccessor parameterAccessor) {
			for (QueryPostProcessor delegate : this.delegates) {
				delegate.postProcess(query, parameterAccessor);
			}
		}
	}

	private enum QueryMethodPostProcessor implements QueryPostProcessor {
		WHERE {
			@Override
			public void postProcess(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				Iterator<Object> parameters = parameterAccessor.iterator();
				for (OrPart node : query.repositoryQuery.tree) {
					Iterator<Part> parts = node.iterator();
					Assert.state(parts.hasNext(), () -> "No part found in PartTree " + query.repositoryQuery.tree);
					do {
						query.applyWhere(parts.next(), parameters);
					} while (parts.hasNext());
					query.root.or();
				}
			}
		},
		FROM {
			@Override
			public void postProcess(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				if (query.repositoryQuery.queryMethod.getDomainClass() == query.repositoryQuery.queryMethod.getReturnedObjectType()) {
					return;
				}
				Class<?> type = parameterAccessor.findDynamicProjection();
				if (type == null) {
					type = query.repositoryQuery.queryMethod.getReturnedObjectType();
				}
				ReturnedType projectionType = ReturnedType.of(type, query.repositoryQuery.queryMethod.getDomainClass(), query.repositoryQuery.queryMethod.getFactory());
				query.projectionType = projectionType;
				if (projectionType.hasInputProperties()) {
					query.root.select(projectionType.getInputProperties().toArray(String[]::new));
				}
			}
		},
		SORT {
			@Override
			public void postProcess(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				Sort sort = parameterAccessor.getSort();
				for (Order order : sort) {
					query.root.sort(order.getProperty(), order.isDescending());
				}
			}
		},
		PAGEABLE {
			@Override
			public void postProcess(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				Pageable pageable = parameterAccessor.getPageable();
				if (pageable.isPaged()) {
					query.root.limit(pageable.getPageSize()).offset((int) pageable.getOffset()).reqTotal();
				}
			}
		}
	}

	private interface QueryExecution {
		Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor);
	}

	private static final class DelegatingQueryExecution implements QueryExecution {

		private final List<QueryMethodExecution> executions;

		private DelegatingQueryExecution(QueryMethodExecution... executions) {
			this.executions = List.of(executions);
		}

		@Override
		public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
			for (QueryMethodExecution execution : this.executions) {
				if (execution.supports(query)) {
					return execution.execute(query, parameterAccessor);
				}
			}
			return fallbackToSingleResultQuery(query);
		}

		private Object fallbackToSingleResultQuery(ReindexerQuery query) {
			Object entity = toEntity(query);
			if (query.repositoryQuery.queryMethod.isOptionalQuery()) {
				return Optional.ofNullable(entity);
			}
			Assert.state(entity != null, "Exactly one item expected, but there is zero");
			return entity;
		}

		private Object toEntity(ReindexerQuery query) {
			try (ResultIterator<?> iterator = new ProjectingResultIterator(query)) {
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
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				try (ResultIterator<?> iterator = new ProjectingResultIterator(query)) {
					Collection<Object> result = CollectionFactory.createCollection(query.repositoryQuery.queryMethod.getReturnType(),
							query.repositoryQuery.queryMethod.getReturnedObjectType(), (int) iterator.size());
					while (iterator.hasNext()) {
						result.add(iterator.next());
					}
					return result;
				}
			}

			@Override
			public boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.queryMethod.isCollectionQuery()
						&& !query.repositoryQuery.queryMethod.getParameters().hasPageableParameter();
			}
		},
		STREAM {
			@Override
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				ResultIterator<?> iterator = new ProjectingResultIterator(query);
				Spliterator<?> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
				return StreamSupport.stream(spliterator, false).onClose(iterator::close);
			}

			@Override
			public boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.queryMethod.isStreamQuery();
			}
		},
		ITERATOR {
			@Override
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				return new ProjectingResultIterator(query);
			}

			@Override
			public boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.queryMethod.isIteratorQuery();
			}
		},
		PAGEABLE {
			@Override
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				try (ProjectingResultIterator iterator = new ProjectingResultIterator(query)) {
					List<Object> content = new ArrayList<>();
					while (iterator.hasNext()) {
						content.add(iterator.next());
					}
					if (query.repositoryQuery.queryMethod.isPageQuery()) {
						Pageable pageable = parameterAccessor.getPageable();
						return pageable.isPaged() ? PageableExecutionUtils.getPage(content, pageable, iterator::getTotalCount)
								: new PageImpl<>(content);
					}
					if (query.repositoryQuery.queryMethod.isListQuery()) {
						return content;
					}
					throw new IllegalStateException("Unsupported return type for Pageable query " + query.repositoryQuery.queryMethod.getReturnType());
				}
			}

			@Override
			public boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.queryMethod.getParameters().hasPageableParameter();
			}
		},
		COUNT {
			@Override
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				return query.root.count();
			}

			@Override
			boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.tree.isCountProjection();
			}
		},
		EXISTS {
			@Override
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				return query.root.exists();
			}

			@Override
			boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.tree.isExistsProjection();
			}
		},
		DELETE {
			@Override
			public Object execute(ReindexerQuery query, ParameterAccessor parameterAccessor) {
				query.root.delete();
				return null;
			}

			@Override
			boolean supports(ReindexerQuery query) {
				return query.repositoryQuery.tree.isDelete();
			}
		};
		abstract boolean supports(ReindexerQuery query);
	}

	private static final class ProjectingResultIterator implements ResultIterator<Object> {

		private static final Map<Class<?>, Constructor<?>> cache = new ConcurrentHashMap<>();

		private final ReindexerQuery query;

		private final ResultIterator<?> delegate;

		private ProjectingResultIterator(ReindexerQuery query) {
			this.query = query;
			this.delegate = query.root.execute();
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
			if (this.query.projectionType != null) {
				if (this.query.projectionType.getReturnedType().isInterface()) {
					return this.query.repositoryQuery.queryMethod.getFactory().createProjection(this.query.projectionType.getReturnedType(), item);
				}
				List<String> properties = this.query.projectionType.getInputProperties();
				Object[] values = new Object[properties.size()];
				for (int i = 0; i < properties.size(); i++) {
					values[i] = BeanPropertyUtils.getProperty(item, properties.get(i));
				}
				Constructor<?> constructor = cache.computeIfAbsent(this.query.projectionType.getReturnedType(), (type) -> {
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

		private final ReindexerRepositoryQuery repositoryQuery;

		private final Query<?> root;

		private ReturnedType projectionType;

		private ReindexerQuery(ReindexerRepositoryQuery repositoryQuery) {
			this.repositoryQuery = repositoryQuery;
			this.root = repositoryQuery.namespace.query();
		}

		private void applyWhere(Part part, Iterator<Object> parameters) {
			String indexName = part.getProperty().toDotPath();
			switch (part.getType()) {
				case GREATER_THAN -> applyWhere(indexName, Condition.GT, parameters);
				case GREATER_THAN_EQUAL -> applyWhere(indexName, Condition.GE, parameters);
				case LESS_THAN -> applyWhere(indexName, Condition.LT, parameters);
				case LESS_THAN_EQUAL -> applyWhere(indexName, Condition.LE, parameters);
				case IN, CONTAINING -> applyWhere(indexName, Condition.SET, parameters);
				case NOT_IN, NOT_CONTAINING -> applyWhereNot(indexName, Condition.SET, parameters);
				case IS_NOT_NULL -> this.root.isNotNull(indexName);
				case IS_NULL -> this.root.isNull(indexName);
				case SIMPLE_PROPERTY -> applyWhere(indexName, Condition.EQ, parameters);
				case NEGATING_SIMPLE_PROPERTY -> applyWhereNot(indexName, Condition.EQ, parameters);
				default -> throw new IllegalArgumentException("Unsupported keyword!");
			}
		}

		private void applyWhereNot(String indexName, Condition condition, Iterator<Object> parameters) {
			this.root.not();
			applyWhere(indexName, condition, parameters);
		}

		private void applyWhere(String indexName, Condition condition, Iterator<Object> parameters) {
			Object value = getParameterValue(indexName, parameters.next());
			if (value instanceof Collection<?> values) {
				this.root.where(indexName, condition, values);
			}
			else {
				this.root.where(indexName, condition, value);
			}
		}

		private Object getParameterValue(String indexName, Object value) {
			if (value instanceof Enum<?>) {
				ReindexerIndex index = this.repositoryQuery.indexes.get(indexName);
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
	}

}
