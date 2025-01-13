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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.domain.Pageable;
import org.springframework.data.reindexer.repository.support.TransactionalNamespace;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Lazy;
import org.springframework.util.Assert;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod queryMethod;

	private final ReindexerEntityInformation<?, ?> entityInformation;

	private final Namespace<?> namespace;

	private final PartTree tree;

	private final Map<String, ReindexerIndex> indexes;

	private final Lazy<QueryExecution> queryExecution;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link ReindexerQueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use                         
	 */
	public ReindexerRepositoryQuery(ReindexerQueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		this.entityInformation = entityInformation;
		ReindexerNamespace<?> namespace = (ReindexerNamespace<?>) reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.indexes = namespace.getIndexes().stream().collect(Collectors.toUnmodifiableMap(ReindexerIndex::getName, Function.identity()));
		this.namespace = new TransactionalNamespace<>(namespace);
		this.tree = new PartTree(queryMethod.getName(), entityInformation.getJavaType());
		this.queryExecution = Lazy.of(() -> {
			if (queryMethod.isCollectionQuery()) {
				return QueryMethodExecution.COLLECTION;
			}
			if (queryMethod.isStreamQuery()) {
				return QueryMethodExecution.STREAM;
			}
			if (queryMethod.isIteratorQuery()) {
				return QueryMethodExecution.ITERATOR;
			}
			if (queryMethod.isPageQuery()) {
				return QueryMethodExecution.PAGEABLE;
			}
			if (tree.isCountProjection()) {
				return QueryMethodExecution.COUNT;
			}
			if (tree.isExistsProjection()) {
				return QueryMethodExecution.EXISTS;
			}
			if (tree.isDelete()) {
				return QueryMethodExecution.DELETE;
			}
			return (queryCreator) -> {
				Object entity = QueryMethodExecution.SINGLE_ENTITY.execute(queryCreator);
				if (queryMethod.isOptionalQuery()) {
					return Optional.ofNullable(entity);
				}
				Assert.state(entity != null, "Exactly one item expected, but there is zero");
				return entity;
			};
		});
	}

	@Override
	public Object execute(Object[] parameters) {
		ParameterAccessor parameterAccessor = new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		ResultProcessor resultProcessor = this.queryMethod.getResultProcessor().withDynamicProjection(parameterAccessor);
		ReindexerQueryCreator queryCreator = new ReindexerQueryCreator(this.tree, this.namespace, this.entityInformation,
				this.queryMethod, this.indexes, parameterAccessor, resultProcessor.getReturnedType());
		Object result = this.queryExecution.get().execute(queryCreator);
		return resultProcessor.processResult(result);
	}

	@Override
	public ReindexerQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	@FunctionalInterface
	private interface QueryExecution {
		Object execute(ReindexerQueryCreator queryCreator);
	}

	private enum QueryMethodExecution implements QueryExecution {
		COLLECTION {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				try (ResultIterator<?> iterator = new ProjectingResultIterator(queryCreator.createQuery(), queryCreator.getReturnedType())) {
					List<Object> result = new ArrayList<>();
					while (iterator.hasNext()) {
						Object next = iterator.next();
						if (next != null) {
							result.add(next);
						}
					}
					return result;
				}
			}
		},
		STREAM {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				ResultIterator<?> iterator = new ProjectingResultIterator(queryCreator.createQuery(), queryCreator.getReturnedType());
				Spliterator<?> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
				return StreamSupport.stream(spliterator, false).onClose(iterator::close);
			}
		},
		ITERATOR {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				return new ProjectingResultIterator(queryCreator.createQuery(), queryCreator.getReturnedType());
			}
		},
		PAGEABLE {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				try (ProjectingResultIterator iterator = new ProjectingResultIterator(queryCreator.createQuery().reqTotal(), queryCreator.getReturnedType())) {
					List<Object> content = new ArrayList<>();
					while (iterator.hasNext()) {
						content.add(iterator.next());
					}
					Pageable pageable = queryCreator.getParameters().getPageable();
					return PageableExecutionUtils.getPage(content, pageable, iterator::getTotalCount);
				}
			}
		},
		COUNT {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				return queryCreator.createQuery().count();
			}
		},
		EXISTS {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				return queryCreator.createQuery().exists();
			}
		},
		DELETE {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				queryCreator.createQuery().delete();
				return null;
			}
		},
		SINGLE_ENTITY {
			@Override
			public Object execute(ReindexerQueryCreator queryCreator) {
				try (ResultIterator<?> iterator = new ProjectingResultIterator(queryCreator.createQuery(), queryCreator.getReturnedType())) {
					Object item = null;
					if (iterator.hasNext()) {
						item = iterator.next();
					}
					if (iterator.hasNext()) {
						throw new IllegalStateException("Exactly one item expected, but there are more");
					}
					return item;
				}
				catch (Exception e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		}
	}
}
