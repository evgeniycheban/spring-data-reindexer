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

import java.lang.reflect.Method;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.Transaction;

import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.repository.query.QueryParameterMapper;
import org.springframework.data.reindexer.repository.query.ReindexerParameterAccessor;
import org.springframework.data.reindexer.repository.query.ReindexerParameters;
import org.springframework.data.reindexer.repository.util.StringQueryUtils;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.data.util.Lazy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerAotRepositoryFragmentSupport {

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final ReindexerConverter converter;

	private final ValueExpressionDelegate valueExpressionDelegate;

	private final ProjectionFactory projectionFactory;

	private final Lazy<QueryParameterMapper> parameterMapper;

	private final Lazy<ConcurrentLruCache<Method, ReindexerParameters>> parameters;

	protected ReindexerAotRepositoryFragmentSupport(Reindexer reindexer, ReindexerMappingContext mappingContext,
			ReindexerConverter converter, RepositoryFactoryBeanSupport.FragmentCreationContext context) {
		this(reindexer, mappingContext, converter, context.getRepositoryMetadata(),
				context.getValueExpressionDelegate(), context.getProjectionFactory());
	}

	protected ReindexerAotRepositoryFragmentSupport(Reindexer reindexer, ReindexerMappingContext mappingContext,
			ReindexerConverter converter, RepositoryMetadata metadata, ValueExpressionDelegate valueExpressionDelegate,
			ProjectionFactory projectionFactory) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.converter = converter;
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.projectionFactory = projectionFactory;
		this.parameterMapper = Lazy
			.of(() -> new QueryParameterMapper(metadata.getDomainType(), mappingContext, converter));
		this.parameters = Lazy
			.of(() -> new ConcurrentLruCache<>(32, (it) -> new ReindexerParameters(ParametersSource.of(metadata, it))));
	}

	@SuppressWarnings("unchecked")
	protected <T> Query<T> query(Class<T> domainType) {
		Namespace<T> namespace = openNamespace(domainType);
		Transaction<T> transaction = (Transaction<T>) TransactionSynchronizationManager.getResource(namespace);
		return (transaction) != null ? transaction.query() : namespace.query();
	}

	protected <T> Namespace<T> openNamespace(Class<T> domainType) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(domainType);
		return this.reindexer.openNamespace(entity.getNamespace(), entity.getNamespaceOptions(), domainType);
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

	protected Object[] mapParameterValues(String indexName, Object... values) {
		return this.parameterMapper.get().mapParameterValues(indexName, values);
	}

	protected Object mapParameterValue(String indexName, Object value) {
		return this.parameterMapper.get().mapParameterValue(indexName, value);
	}

	protected String substituteQueryParameters(String query, Method method, Object... values) {
		ReindexerParameterAccessor parameters = new ReindexerParameterAccessor(this.parameters.get().get(method),
				values);
		return StringQueryUtils.substituteQueryParameters(query, parameters,
				this.valueExpressionDelegate.getEvaluationContextAccessor());
	}

}
