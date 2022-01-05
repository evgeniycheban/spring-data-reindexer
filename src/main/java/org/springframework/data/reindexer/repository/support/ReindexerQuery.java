package org.springframework.data.reindexer.repository.support;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.NamespaceOptions;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerQuery implements RepositoryQuery {

	private final QueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final PartTree tree;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link QueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use                         
	 */
	public ReindexerQuery(QueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		this.namespace = reindexer.openNamespace(entityInformation.getNamespaceName(), NamespaceOptions.defaultOptions(),
				queryMethod.getEntityInformation().getJavaType());
		this.tree = new PartTree(queryMethod.getName(), queryMethod.getEntityInformation().getJavaType());
	}

	@Override
	public Object execute(Object[] parameters) {
		ParametersParameterAccessor accessor =
				new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		ReindexerQueryCreator<?> queryCreator = new ReindexerQueryCreator<>(this.tree, accessor, this.namespace);
		Query<?> query = queryCreator.createQuery();
		if (this.queryMethod.isCollectionQuery()) {
			return query.toList();
		}
		if (this.queryMethod.isStreamQuery()) {
			return query.stream();
		}
		return query.findOne();
	}

	@Override
	public QueryMethod getQueryMethod() {
		return this.queryMethod;
	}

}
