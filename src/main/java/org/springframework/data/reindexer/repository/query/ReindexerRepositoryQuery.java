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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.ReindexerNamespace;

import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.support.TransactionalNamespace;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Lazy;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod method;

	private final ReindexerEntityInformation<?, ?> entityInformation;

	private final ReindexerMappingContext mappingContext;

	private final Reindexer reindexer;

	private final Namespace<?> namespace;

	private final PartTree tree;

	private final Map<String, ReindexerIndex> indexes;

	private final ReindexerConverter reindexerConverter;

	private final Lazy<Function<ReindexerQueryCreator, Object>> queryExecution;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public ReindexerRepositoryQuery(ReindexerQueryMethod method, ReindexerEntityInformation<?, ?> entityInformation,
			ReindexerMappingContext mappingContext, Reindexer reindexer, ReindexerConverter reindexerConverter) {
		this.method = method;
		this.entityInformation = entityInformation;
		this.mappingContext = mappingContext;
		this.reindexer = reindexer;
		this.reindexerConverter = reindexerConverter;
		ReindexerNamespace<?> namespace = (ReindexerNamespace<?>) reindexer.openNamespace(
				entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.indexes = namespace.getIndexes()
			.stream()
			.collect(Collectors.toUnmodifiableMap(ReindexerIndex::getName, Function.identity()));
		this.namespace = new TransactionalNamespace<>(namespace);
		this.tree = new PartTree(method.getName(), entityInformation.getJavaType());
		this.queryExecution = Lazy.of(() -> {
			if (method.isCollectionQuery()) {
				return (creator) -> ReindexerQueryExecutions.toList(toIterator(creator));
			}
			if (method.isStreamQuery()) {
				return (creator) -> ReindexerQueryExecutions.toStream(toIterator(creator));
			}
			if (method.isIteratorQuery()) {
				return this::toIterator;
			}
			if (method.isPageQuery()) {
				return (creator) -> {
					ProjectingResultIterator<?, ?> iterator = new ProjectingResultIterator<>(
							creator.createQuery().reqTotal(), creator.getReturnedType(), reindexerConverter);
					return PageableExecutionUtils.getPage(ReindexerQueryExecutions.toList(iterator),
							creator.getParameters().getPageable(), iterator::getTotalCount);
				};
			}
			if (this.tree.isCountProjection()) {
				return (creator) -> creator.createQuery().count();
			}
			if (this.tree.isExistsProjection()) {
				return (creator) -> creator.createQuery().exists();
			}
			if (this.tree.isDelete()) {
				return (creator) -> {
					creator.createQuery().delete();
					return null;
				};
			}
			return (creator) -> ReindexerQueryExecutions.toEntity(toIterator(creator));
		});
	}

	private ProjectingResultIterator<?, ?> toIterator(ReindexerQueryCreator queryCreator) {
		return new ProjectingResultIterator<>(queryCreator.createQuery(), queryCreator.getReturnedType(),
				this.reindexerConverter);
	}

	@Override
	public Object execute(Object[] parameters) {
		ReindexerParameterAccessor parameterAccessor = new ReindexerParameterAccessor(this.method.getParameters(),
				parameters);
		ResultProcessor resultProcessor = this.method.getResultProcessor().withDynamicProjection(parameterAccessor);
		ReindexerQueryCreator queryCreator = new ReindexerQueryCreator(this.tree, this.reindexer, this.namespace,
				this.entityInformation, this.mappingContext, this.indexes, this.reindexerConverter, parameterAccessor,
				resultProcessor.getReturnedType());
		Object result = this.queryExecution.get().apply(queryCreator);
		return resultProcessor.processResult(result);
	}

	@Override
	public ReindexerQueryMethod getQueryMethod() {
		return this.method;
	}

}