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
package org.springframework.data.reindexer.repository.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Optional;

import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import org.springframework.data.reindexer.repository.query.ReindexerRepositoryQuery;
import org.springframework.data.reindexer.repository.query.StringBasedReindexerRepositoryQuery;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.Assert;

/**
 * Factory to create {@link ReindexerRepository} instances.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryFactory extends RepositoryFactorySupport {

	private final Reindexer reindexer;

	/**
	 * Creates an instance.
	 *
	 * @param reindexer the {@link Reindexer} to use
	 */
	public ReindexerRepositoryFactory(Reindexer reindexer) {
		this.reindexer = reindexer;
	}

	@Override
	public <T, ID> ReindexerEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
		return MappingReindexerEntityInformation.getInstance(domainClass);
	}

	@Override
	protected RepositoryMetadata getRepositoryMetadata(Class<?> repositoryInterface) {
		Assert.notNull(repositoryInterface, "Repository interface must not be null");
		return Repository.class.isAssignableFrom(repositoryInterface) ? new ReindexerDefaultRepositoryMetadata(repositoryInterface)
				: new ReindexerAnnotationRepositoryMetadata(repositoryInterface);
	}

	@Override
	protected Object getTargetRepository(RepositoryInformation metadata) {
		EntityInformation<?, Serializable> entityInformation = getEntityInformation(metadata.getDomainType());
		return getTargetRepositoryViaReflection(metadata, entityInformation, this.reindexer);
	}

	@Override
	protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
		return SimpleReindexerRepository.class;
	}

	@Override
	protected Optional<QueryLookupStrategy> getQueryLookupStrategy(QueryLookupStrategy.Key key, QueryMethodEvaluationContextProvider evaluationContextProvider) {
		return Optional.of(new ReindexerQueryLookupStrategy());
	}

	private class ReindexerQueryLookupStrategy implements QueryLookupStrategy {

		@Override
		public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory, NamedQueries namedQueries) {
			ReindexerQueryMethod queryMethod = new ReindexerQueryMethod(method, metadata, factory);
			if (queryMethod.hasQueryAnnotation()) {
				return new StringBasedReindexerRepositoryQuery(queryMethod, getEntityInformation(metadata.getDomainType()),
						ReindexerRepositoryFactory.this.reindexer);
			}
			return new ReindexerRepositoryQuery(queryMethod, getEntityInformation(metadata.getDomainType()),
					ReindexerRepositoryFactory.this.reindexer);
		}

	}

}
