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
package org.springframework.data.reindexer.repository.query;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

	private final Lazy<Boolean> isOptionalQuery;

	private final Lazy<Boolean> isListQuery;

	private final Lazy<Boolean> isSetQuery;

	private final Lazy<Query> queryAnnotationExtractor;

	private final Class<?> returnType;

	private final ProjectionFactory factory;

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
		this.isIteratorQuery = Lazy.of(() -> Iterator.class.isAssignableFrom(method.getReturnType()));
		this.isOptionalQuery = Lazy.of(() -> Optional.class.isAssignableFrom(method.getReturnType()));
		this.isListQuery = Lazy.of(() -> List.class.isAssignableFrom(method.getReturnType()));
		this.isSetQuery = Lazy.of(() -> Set.class.isAssignableFrom(method.getReturnType()));
		this.queryAnnotationExtractor = Lazy.of(() -> method.getAnnotation(Query.class));
		this.returnType = method.getReturnType();
		this.factory = factory;
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
	 * Returns true if the method returns {@link Optional}.
	 *
	 * @return true if the method returns {@link Optional}
	 * @since 1.1
	 */
	public boolean isOptionalQuery() {
		return this.isOptionalQuery.get();
	}

	/**
	 * Returns true if the method returns {@link List}.
	 *
	 * @return true if the method returns {@link List}
	 * @since 1.1
	 */
	public boolean isListQuery() {
		return this.isListQuery.get();
	}

	/**
	 * Returns true if the method returns {@link Set}.
	 *
	 * @return true if the method returns {@link Set}
	 * @since 1.1
	 */
	public boolean isSetQuery() {
		return this.isSetQuery.get();
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

	/**
	 * Returns true, if the query is for UPDATE.
	 *
	 * @return true, if the query is for UPDATE
	 */
	public boolean isUpdateQuery() {
		Query query = this.queryAnnotationExtractor.get();
		return query.update();
	}

	/**
	 * Returns method's return type
	 *
	 * @return the method's return type
	 */
	Class<?> getReturnType() {
		return this.returnType;
	}

	/**
	 * Returns a {@link ProjectionFactory} to be used.
	 *
	 * @return the {@link ProjectionFactory} to use
	 */
	ProjectionFactory getFactory() {
		return this.factory;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> getDomainClass() {
		return super.getDomainClass();
	}

}
