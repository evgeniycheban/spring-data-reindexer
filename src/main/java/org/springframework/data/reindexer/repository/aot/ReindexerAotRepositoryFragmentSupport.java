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
package org.springframework.data.reindexer.repository.aot;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.Transaction;

import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.repository.query.QueryParameterMapper;
import org.springframework.data.util.Lazy;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerAotRepositoryFragmentSupport {

	private final SpelExpressionParser expressionParser = new SpelExpressionParser();

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory(
			this.expressionParser);

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final ReindexerConverter converter;

	private final Lazy<QueryParameterMapper> parameterMapper;

	protected ReindexerAotRepositoryFragmentSupport(Reindexer reindexer, ReindexerMappingContext mappingContext,
			ReindexerConverter converter, Class<?> domainType) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.converter = converter;
		this.parameterMapper = Lazy.of(() -> createParameterMapper(domainType));
	}

	private QueryParameterMapper createParameterMapper(Class<?> domainType) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(domainType);
		ReindexerNamespace<?> namespace = (ReindexerNamespace<?>) this.reindexer.openNamespace(entity.getNamespace(),
				entity.getNamespaceOptions(), domainType);
		Map<String, ReindexerIndex> mappedIndexes = namespace.getIndexes()
			.stream()
			.collect(Collectors.toUnmodifiableMap(ReindexerIndex::getName, Function.identity()));
		return new QueryParameterMapper(domainType, mappedIndexes, this.mappingContext, this.converter);
	}

	@SuppressWarnings("unchecked")
	protected <T> Query<T> query(Class<T> domainType) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(domainType);
		Namespace<T> namespace = this.reindexer.openNamespace(entity.getNamespace(), entity.getNamespaceOptions(),
				domainType);
		Transaction<T> transaction = (Transaction<T>) TransactionSynchronizationManager.getResource(namespace);
		return (transaction) != null ? transaction.query() : namespace.query();
	}

	protected ProjectionFactory getProjectionFactory() {
		return this.projectionFactory;
	}

	protected ReindexerMappingContext getMappingContext() {
		return this.mappingContext;
	}

	protected ReindexerConverter getReindexerConverter() {
		return this.converter;
	}

	protected QueryParameterMapper getParameterMapper() {
		return this.parameterMapper.get();
	}

}
