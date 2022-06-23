package org.springframework.data.reindexer.repository.support;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * A string-based {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class StringBasedReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link QueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use
	 */
	public StringBasedReindexerRepositoryQuery(ReindexerQueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		this.namespace = reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
	}

	@Override
	public Object execute(Object[] parameters) {
		String query = String.format(this.queryMethod.getQuery(), parameters);
		if (this.queryMethod.isUpdateQuery()) {
			this.namespace.updateSql(query);
			return null;
		}
		if (this.queryMethod.isIteratorQuery()) {
			return this.namespace.execSql(query);
		}
		throw new IllegalStateException("Unsupported method return type " + this.queryMethod.getReturnedObjectType());
	}

	@Override
	public QueryMethod getQueryMethod() {
		return this.queryMethod;
	}

}
