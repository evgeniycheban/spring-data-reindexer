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

import java.util.List;
import java.util.function.BiFunction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.Namespace;

import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.reindexer.repository.util.StringQueryUtils;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Lazy;

/**
 * A simple string-based {@link RepositoryQuery} implementation that provides only
 * parameter binding, with no support for parameter type conversion. This is mainly used
 * as a fallback when {@link StringBasedReindexerQuery} cannot be used by the application
 * or if the {@link Query#nativeQuery()} is explicitly set to {@literal true}.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class SimpleStringBasedReindexerQuery implements RepositoryQuery {

	private final ReindexerQueryMethod method;

	private final ReindexerConverter reindexerConverter;

	private final Namespace<?> namespace;

	private final QueryMethodValueEvaluationContextAccessor factory;

	private final Lazy<BiFunction<ReindexerParameterAccessor, ReturnedType, Object>> queryExecution;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 * @param namespace the {@link Namespace} to use
	 * @param factory the {@link QueryMethodValueEvaluationContextAccessor} to use
	 */
	public SimpleStringBasedReindexerQuery(ReindexerQueryMethod method, ReindexerConverter reindexerConverter,
			Namespace<?> namespace, QueryMethodValueEvaluationContextAccessor factory) {
		this.method = method;
		this.reindexerConverter = reindexerConverter;
		this.namespace = namespace;
		this.factory = factory;
		this.queryExecution = Lazy.of(() -> getQueryExecution(method));
	}

	@Override
	public @Nullable Object execute(Object @NonNull [] parameters) {
		ReindexerParameterAccessor accessor = new ReindexerParameterAccessor(this.method.getParameters(), parameters);
		ResultProcessor resultProcessor = this.method.getResultProcessor().withDynamicProjection(accessor);
		Object result = this.queryExecution.get().apply(accessor, resultProcessor.getReturnedType());
		return resultProcessor.processResult(result);
	}

	@Override
	public @NonNull QueryMethod getQueryMethod() {
		return this.method;
	}

	private BiFunction<ReindexerParameterAccessor, ReturnedType, Object> getQueryExecution(
			ReindexerQueryMethod method) {
		if (method.isSearchQuery()) {
			return getSearchQueryExecution(method);
		}
		if (method.isCollectionQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toList(toResultAccessor(parameters, returnedType));
		}
		if (method.isPageQuery()) {
			return (parameters, returnedType) -> {
				ReindexerResultAccessor<?> it = toResultAccessor(parameters, returnedType);
				return PageableExecutionUtils.getPage(ReindexerQueryExecutions.toList(it), parameters.getPageable(),
						it::getTotalCount);
			};
		}
		if (method.isSliceQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toSlice(toResultAccessor(parameters, returnedType), parameters.getPageable());
		}
		if (method.isStreamQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toStream(toResultAccessor(parameters, returnedType));
		}
		if (method.isIteratorQuery()) {
			return this::toResultAccessor;
		}
		if (method.isModifyingQuery()) {
			return (parameters, returnedType) -> {
				String preparedQuery = StringQueryUtils.substituteQueryParameters(this.method.getQuery(), parameters,
						this.factory);
				this.namespace.updateSql(preparedQuery);
				return null;
			};
		}
		return (parameters, returnedType) -> ReindexerQueryExecutions
			.toEntity(toResultAccessor(parameters, returnedType));
	}

	@SuppressWarnings("unchecked")
	private BiFunction<ReindexerParameterAccessor, ReturnedType, Object> getSearchQueryExecution(
			ReindexerQueryMethod method) {
		if (method.isStreamQuery()) {
			return (parameters, returnedType) -> {
				ReindexerResultAccessor<?> it = toResultAccessor(parameters, returnedType);
				return ReindexerQueryExecutions.toStream(it).map((e) -> new SearchResult<>(e, it.getCurrentRank()));
			};
		}
		if (method.isCollectionQuery()) {
			return (parameters, returnedType) -> ReindexerQueryExecutions
				.toSearchResults(toResultAccessor(parameters, returnedType), List.class);
		}
		return (parameters,
				returnedType) -> new SearchResults<>((List<? extends SearchResult<Object>>) ReindexerQueryExecutions
					.toSearchResults(toResultAccessor(parameters, returnedType), List.class));
	}

	private ReindexerResultAccessor<?> toResultAccessor(ReindexerParameterAccessor parameters,
			ReturnedType returnedType) {
		String preparedQuery = StringQueryUtils.substituteQueryParameters(this.method.getQuery(), parameters,
				this.factory);
		return new ProjectingResultIterator<>(this.namespace.execSql(preparedQuery), returnedType,
				this.reindexerConverter);
	}

}
