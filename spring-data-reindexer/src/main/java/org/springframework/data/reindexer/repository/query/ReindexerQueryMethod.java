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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.reindexer.core.annotation.Collation;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.util.Lazy;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * Reindexer-specific {@link QueryMethod}.
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerQueryMethod extends QueryMethod {

	private final Lazy<Boolean> isIteratorQuery;

	private final Lazy<Query> queryAnnotationExtractor;

	private final Method method;

	private final Map<Class<? extends Annotation>, Optional<Annotation>> annotationCache;

	/**
	 * Creates a new {@link QueryMethod} from the given parameters. Looks up the correct
	 * query to use for the following invocations of the method given.
	 * @param method must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @param factory must not be {@literal null}.
	 */
	public ReindexerQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
		super(method, metadata, factory, ReindexerParameters::new);
		this.isIteratorQuery = Lazy.of(() -> Iterator.class.isAssignableFrom(method.getReturnType()));
		this.queryAnnotationExtractor = Lazy.of(() -> method.getAnnotation(Query.class));
		this.method = method;
		this.annotationCache = new ConcurrentReferenceHashMap<>();
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
	 * Returns {@literal true}, if the query is for UPDATE.
	 * @return {@literal true}, if the query is for UPDATE
	 */
	@Override
	public boolean isModifyingQuery() {
		Query query = this.queryAnnotationExtractor.get();
		return query.update();
	}

	/**
	 * Returns {@literal true} if the query is a native Reindexer query.
	 * @return true, if the query is a native Reindexer query
	 * @since 1.6
	 */
	public boolean isNativeQuery() {
		Query query = this.queryAnnotationExtractor.get();
		return query.nativeQuery();
	}

	/**
	 * Check if the query method is decorated with a non-empty {@link Collation#value()}.
	 * @return true if method annotated with {@link Collation} having a non-empty
	 * collation attribute
	 * @since 1.7
	 */
	public boolean hasAnnotatedCollation() {
		return doFindAnnotation(Collation.class).map(Collation::value).filter(StringUtils::hasText).isPresent();
	}

	/**
	 * Get the collation value extracted from the {@link Collation} annotation.
	 * @return the {@link Collation#value()} to use
	 * @throws IllegalStateException if method is not annotated with {@link Collation} or
	 * having an empty value. Make sure to check {@link #hasAnnotatedCollation()}} first.
	 * @since 1.7
	 */
	public String getAnnotatedCollation() {
		return doFindAnnotation(Collation.class).map(Collation::value) //
			.orElseThrow(() -> new IllegalStateException(
					"Expected to find @Collation annotation but did not; Make sure to check hasAnnotatedCollation() before."));
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
	public ReindexerParameters getParameters() {
		return (ReindexerParameters) super.getParameters();
	}

	@SuppressWarnings("unchecked")
	private <A extends Annotation> Optional<A> doFindAnnotation(Class<A> annotationType) {
		return (Optional<A>) this.annotationCache.computeIfAbsent(annotationType,
				it -> Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(this.method, annotationType)));
	}

}
