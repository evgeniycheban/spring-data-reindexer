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
package org.springframework.data.reindexer.repository.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.ReindexerNamespace;

import org.springframework.context.ApplicationContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.query.PartTreeReindexerQuery;
import org.springframework.data.reindexer.repository.query.QueryParameterMapper;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import org.springframework.data.reindexer.repository.query.SimpleStringBasedReindexerQuery;
import org.springframework.data.reindexer.repository.query.StringBasedReindexerQuery;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Factory to create {@link ReindexerRepository} instances.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryFactory extends RepositoryFactorySupport {

	private static final Log LOG = LogFactory.getLog(ReindexerRepositoryFactory.class);

	private static final boolean USE_VISITOR_BASED_QUERY = ClassUtils.isPresent("net.sf.jsqlparser.parser.CCJSqlParser",
			ReindexerRepositoryFactory.class.getClassLoader());

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final ApplicationContext ctx;

	private final ReindexerConverter reindexerConverter;

	/**
	 * Creates an instance.
	 * @param reindexer the {@link Reindexer} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 * @param ctx the {@link ApplicationContext} to use
	 */
	public ReindexerRepositoryFactory(Reindexer reindexer, ReindexerMappingContext mappingContext,
			ReindexerConverter reindexerConverter, ApplicationContext ctx) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.reindexerConverter = reindexerConverter;
		this.ctx = ctx;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, ID> ReindexerEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
		ReindexerPersistentEntity<T> persistentEntity = (ReindexerPersistentEntity<T>) this.mappingContext
			.getRequiredPersistentEntity(domainClass);
		return new MappingReindexerEntityInformation<>(persistentEntity);
	}

	@Override
	protected RepositoryMetadata getRepositoryMetadata(Class<?> repositoryInterface) {
		Assert.notNull(repositoryInterface, "Repository interface must not be null");
		return Repository.class.isAssignableFrom(repositoryInterface)
				? new ReindexerDefaultRepositoryMetadata(repositoryInterface)
				: new ReindexerAnnotationRepositoryMetadata(repositoryInterface);
	}

	@Override
	protected Object getTargetRepository(RepositoryInformation metadata) {
		EntityInformation<?, Serializable> entityInformation = getEntityInformation(metadata.getDomainType());
		return getTargetRepositoryViaReflection(metadata, entityInformation, this.mappingContext, this.reindexer,
				this.reindexerConverter);
	}

	@Override
	protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
		return SimpleReindexerRepository.class;
	}

	@Override
	protected Optional<QueryLookupStrategy> getQueryLookupStrategy(QueryLookupStrategy.Key key,
			ValueExpressionDelegate valueExpressionDelegate) {
		return Optional.of(new ReindexerQueryLookupStrategy());
	}

	private class ReindexerQueryLookupStrategy implements QueryLookupStrategy {

		@Override
		public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
				NamedQueries namedQueries) {
			ReindexerQueryMethod queryMethod = new ReindexerQueryMethod(method, metadata, factory);
			ReindexerEntityInformation<?, ?> entityInformation = getEntityInformation(metadata.getDomainType());
			ReindexerNamespace<?> namespace = (ReindexerNamespace<?>) ReindexerRepositoryFactory.this.reindexer
				.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
						entityInformation.getJavaType());
			Map<String, ReindexerIndex> mappedIndexes = namespace.getIndexes()
				.stream()
				.collect(Collectors.toMap(ReindexerIndex::getName, Function.identity()));
			QueryParameterMapper queryParameterMapper = new QueryParameterMapper(entityInformation.getJavaType(),
					mappedIndexes, ReindexerRepositoryFactory.this.mappingContext,
					ReindexerRepositoryFactory.this.reindexerConverter);
			if (queryMethod.hasQueryAnnotation()) {
				QueryMethodValueEvaluationContextAccessor accessor = new QueryMethodValueEvaluationContextAccessor(
						ReindexerRepositoryFactory.this.ctx);
				// Use lightweight implementation when nativeQuery = true.
				if (queryMethod.isNativeQuery()) {
					return new SimpleStringBasedReindexerQuery(queryMethod,
							ReindexerRepositoryFactory.this.reindexerConverter, namespace, accessor);
				}
				// Use visitor-based implementation when JSQLParser is on the classpath.
				if (USE_VISITOR_BASED_QUERY) {
					return new StringBasedReindexerQuery(queryMethod,
							ReindexerRepositoryFactory.this.reindexerConverter,
							ReindexerRepositoryFactory.this.reindexer, ReindexerRepositoryFactory.this.mappingContext,
							queryParameterMapper, accessor);
				}
				// Fallbacks to a lightweight implementation.
				if (LOG.isWarnEnabled()) {
					LOG.warn("""
							JSQLParser not found on the classpath. Falling back to %s for query method %s.
							Only native Reindexer SQL is supported.
							Add com.github.jsqlparser:jsqlparser or set `nativeQuery = true` on the query method."""
						.formatted(SimpleStringBasedReindexerQuery.class.getName(), queryMethod));
				}
				return new SimpleStringBasedReindexerQuery(queryMethod,
						ReindexerRepositoryFactory.this.reindexerConverter, namespace, accessor);
			}
			return new PartTreeReindexerQuery(queryMethod, entityInformation,
					ReindexerRepositoryFactory.this.mappingContext, ReindexerRepositoryFactory.this.reindexer,
					queryParameterMapper, ReindexerRepositoryFactory.this.reindexerConverter);
		}

	}

}
