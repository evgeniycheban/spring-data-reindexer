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
				return this::toList;
			}
			if (queryMethod.isStreamQuery()) {
				return (queryCreator) -> {
					ProjectingResultIterator iterator = toIterator(queryCreator);
					Spliterator<Object> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
					return StreamSupport.stream(spliterator, false).onClose(iterator::close);
				};
			}
			if (queryMethod.isIteratorQuery()) {
				return this::toIterator;
			}
			if (queryMethod.isPageQuery()) {
				return (queryCreator) -> {
					try (ProjectingResultIterator it = new ProjectingResultIterator(queryCreator.createQuery().reqTotal(), queryCreator.getReturnedType())) {
						return PageableExecutionUtils.getPage(toList(it), queryCreator.getParameters().getPageable(), it::getTotalCount);
					}
				};
			}
			if (tree.isCountProjection()) {
				return (queryCreator) -> queryCreator.createQuery().count();
			}
			if (tree.isExistsProjection()) {
				return (queryCreator) -> queryCreator.createQuery().exists();
			}
			if (tree.isDelete()) {
				return (queryCreator) -> {
					queryCreator.createQuery().delete();
					return null;
				};
			}
			return (queryCreator) -> {
				Object entity = null;
				try (ProjectingResultIterator it = toIterator(queryCreator)) {
					if (it.hasNext()) {
						entity = it.next();
					}
					if (it.hasNext()) {
						throw new IllegalStateException("Exactly one item expected, but there are more");
					}
				}
				if (queryMethod.isOptionalQuery()) {
					return Optional.ofNullable(entity);
				}
				Assert.state(entity != null, "Exactly one item expected, but there is zero");
				return entity;
			};
		});
	}

	private List<Object> toList(ReindexerQueryCreator queryCreator) {
		try (ProjectingResultIterator it = toIterator(queryCreator)) {
			return toList(it);
		}
	}

	private List<Object> toList(ProjectingResultIterator iterator) {
		List<Object> result = new ArrayList<>();
		while (iterator.hasNext()) {
			Object next = iterator.next();
			if (next != null) {
				result.add(next);
			}
		}
		return result;
	}

	private ProjectingResultIterator toIterator(ReindexerQueryCreator queryCreator) {
		return new ProjectingResultIterator(queryCreator.createQuery(), queryCreator.getReturnedType());
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
}
