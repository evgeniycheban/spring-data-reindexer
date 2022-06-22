package org.springframework.data.reindexer.repository.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * Factory to create {@link ReindexerRepository} instances.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryFactory extends RepositoryFactorySupport {

	private final Reindexer reindexer;

	private final Map<Class<?>, ReindexerEntityInformation<?, ?>> entityInformationCache;

	/**
	 * Creates an instance.
	 *
	 * @param reindexer the {@link Reindexer} to use
	 */
	public ReindexerRepositoryFactory(Reindexer reindexer) {
		this.reindexer = reindexer;
		this.entityInformationCache = new ConcurrentHashMap<>();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, ID> ReindexerEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
		return (ReindexerEntityInformation<T, ID>) entityInformationCache.computeIfAbsent(domainClass, MappingReindexerEntityInformation::new);
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
