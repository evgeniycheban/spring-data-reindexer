/*
 * Copyright 2022-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.reindexer.repository.query;

import java.util.function.Function;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerNamespace;

import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.support.TransactionalNamespace;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
public class PartTreeReindexerQuery extends AbstractReindexerQuery {

	private final ReindexerQueryMethod method;

	private final ReindexerEntityInformation<?, ?> entityInformation;

	private final ReindexerMappingContext mappingContext;

	private final Reindexer reindexer;

	private final Namespace<?> namespace;

	private final PartTree tree;

	private final QueryParameterMapper queryParameterMapper;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @param queryParameterMapper the {@link QueryParameterMapper} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public PartTreeReindexerQuery(ReindexerQueryMethod method, ReindexerEntityInformation<?, ?> entityInformation,
			ReindexerMappingContext mappingContext, Reindexer reindexer, QueryParameterMapper queryParameterMapper,
			ReindexerConverter reindexerConverter) {
		super(method, reindexerConverter);
		this.method = method;
		this.entityInformation = entityInformation;
		this.mappingContext = mappingContext;
		this.reindexer = reindexer;
		this.queryParameterMapper = queryParameterMapper;
		ReindexerNamespace<?> namespace = (ReindexerNamespace<?>) reindexer.openNamespace(
				entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.namespace = new TransactionalNamespace<>(namespace);
		this.tree = new PartTree(method.getName(), entityInformation.getJavaType());
	}

	@Override
	ReindexerQuery createQuery(ReindexerParameterAccessor parameterAccessor, ReturnedType returnedType) {
		ReindexerQueryCreator queryCreator = new ReindexerQueryCreator(this.tree, this.reindexer, this.namespace,
				this.entityInformation, this.mappingContext, this.queryParameterMapper, parameterAccessor, returnedType,
				this.method);
		return new ReindexerQuery(queryCreator.createQuery(), returnedType, parameterAccessor);
	}

	@Override
	Function<ReindexerQuery, Object> getQueryExecution(ReindexerQueryMethod method) {
		if (this.tree.isCountProjection()) {
			return (query) -> query.criteria().count();
		}
		if (this.tree.isExistsProjection()) {
			return (query) -> query.criteria().exists();
		}
		if (this.tree.isDelete()) {
			return (query) -> {
				query.criteria().delete();
				return null;
			};
		}
		return super.getQueryExecution(method);
	}

}