package org.springframework.data.reindexer.repository.support;

import java.util.Iterator;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Custom query creator to create Reindexer criterias.
 *
 * @param <T> the query type
 * @author Evgeniy Cheban
 */
public class ReindexerQueryCreator<T> extends AbstractQueryCreator<Query<T>, Query<T>> {

	private final Namespace<T> namespace;

	/**
	 * Creates an instance.
	 *
	 * @param tree       the {@link PartTree} to use
	 * @param parameters the {@link ParameterAccessor} to use
	 * @param namespace  the {@link Namespace} to use
	 */
	public ReindexerQueryCreator(PartTree tree, ParameterAccessor parameters, Namespace<T> namespace) {
		super(tree, parameters);
		this.namespace = namespace;
	}

	@Override
	protected Query<T> create(Part part, Iterator<Object> iterator) {
		return where(part, this.namespace.query(), iterator);
	}

	@Override
	protected Query<T> and(Part part, Query<T> base, Iterator<Object> iterator) {
		return where(part, base, iterator);
	}

	@Override
	protected Query<T> or(Query<T> base, Query<T> criteria) {
		return base.or().merge(criteria);
	}

	@Override
	protected Query<T> complete(Query<T> criteria, Sort sort) {
		return criteria;
	}

	private Query<T> where(Part part, Query<T> criteria, Iterator<Object> parameters) {
		String indexName = part.getProperty().toDotPath();
		switch (part.getType()) {
			case GREATER_THAN:
				return criteria.where(indexName, Query.Condition.GT, parameters.next());
			case GREATER_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.GE, parameters.next());
			case LESS_THAN:
				return criteria.where(indexName, Query.Condition.LT, parameters.next());
			case LESS_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.LE, parameters.next());
			case IS_NOT_NULL:
				return criteria.isNotNull(indexName);
			case IS_NULL:
				return criteria.isNull(indexName);
			case SIMPLE_PROPERTY:
				return criteria.where(indexName, Query.Condition.EQ, parameters.next());
			case NEGATING_SIMPLE_PROPERTY:
				return criteria.not().where(indexName, Query.Condition.EQ, parameters.next());
			default:
				throw new IllegalArgumentException("Unsupported keyword!");
		}
	}

}
