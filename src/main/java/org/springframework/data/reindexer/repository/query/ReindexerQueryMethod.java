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

import java.lang.reflect.Method;
import java.util.Iterator;

import org.jspecify.annotations.NonNull;

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
	 * Creates a new {@link QueryMethod} from the given parameters. Looks up the correct
	 * query to use for following invocations of the method given.
	 * @param method must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @param factory must not be {@literal null}.
	 */
	public ReindexerQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
		super(method, metadata, factory, ReindexerParameters::new);
		this.isIteratorQuery = Lazy.of(() -> Iterator.class.isAssignableFrom(method.getReturnType()));
		this.queryAnnotationExtractor = Lazy.of(() -> method.getAnnotation(Query.class));
	}

	/**
	 * Returns true if the method's return type is {@link Iterator}.
	 * @return true if the method's return type is {@link Iterator}
	 */
	public boolean isIteratorQuery() {
		return this.isIteratorQuery.get();
	}

	/**
	 * Returns true if the method has {@link Query} annotation.
	 * @return true if the method has {@link Query} annotation
	 */
	public boolean hasQueryAnnotation() {
		return this.queryAnnotationExtractor.getNullable() != null;
	}

	/**
	 * Returns the query from the {@link Query} annotation.
	 * @return the query from the {@link Query} annotation to use
	 */
	public String getQuery() {
		Query query = this.queryAnnotationExtractor.get();
		return query.value();
	}

	/**
	 * Returns true, if the query is for UPDATE.
	 * @return true, if the query is for UPDATE
	 */
	@Override
	public boolean isModifyingQuery() {
		Query query = this.queryAnnotationExtractor.get();
		return query.update();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> getDomainClass() {
		return super.getDomainClass();
	}

	/**
	 * Returns a {@link ReindexerParameters} to access Reindexer-specific parameters.
	 * @return the {@link ReindexerParameters} to use
	 * @since 1.6
	 */
	@Override
	public @NonNull ReindexerParameters getParameters() {
		return (ReindexerParameters) super.getParameters();
	}

}
