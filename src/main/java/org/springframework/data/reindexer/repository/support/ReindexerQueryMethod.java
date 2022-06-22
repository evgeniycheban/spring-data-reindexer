package org.springframework.data.reindexer.repository.support;

import java.lang.reflect.Method;
import java.util.Iterator;

import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.util.Lazy;

/**
 * Reindexer-specific {@link QueryMethod}.
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerQueryMethod extends QueryMethod {

	private final Lazy<Boolean> isIteratorQuery;

	private final Lazy<Query> queryAnnotationExtractor;

	/**
	 * Creates a new {@link QueryMethod} from the given parameters. Looks up the correct query to use for following
	 * invocations of the method given.
	 *
	 * @param method must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @param factory must not be {@literal null}.
	 */
	public ReindexerQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
		super(method, metadata, factory);
		this.isIteratorQuery = Lazy.of(() -> Iterator.class.isAssignableFrom(getReturnedObjectType()));
		this.queryAnnotationExtractor = Lazy.of(() -> method.getAnnotation(Query.class));
	}

	/**
	 * Returns true if the method's return type is {@link Iterator}.
	 *
	 * @return true if the method's return type is {@link Iterator}
	 */
	public boolean isIteratorQuery() {
		return this.isIteratorQuery.get();
	}

	/**
	 * Returns true if the method has {@link Query} annotation.
	 *
	 * @return true if the method has {@link Query} annotation
	 */
	public boolean hasQueryAnnotation() {
		return this.queryAnnotationExtractor.getNullable() != null;
	}

	/**
	 * Returns the query from the {@link Query} annotation.
	 *
	 * @return the query from the {@link Query} annotation to use
	 */
	public String getQuery() {
		Query query = this.queryAnnotationExtractor.get();
		return query.value();
	}

}
