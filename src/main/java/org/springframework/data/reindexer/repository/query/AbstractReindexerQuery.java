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
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import ru.rt.restream.reindexer.Query;

import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Lazy;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
abstract class AbstractReindexerQuery implements RepositoryQuery {

	private final ReindexerQueryMethod method;

	private final ReindexerConverter reindexerConverter;

	private final Lazy<Function<ReindexerQuery, Object>> queryExecution;

	AbstractReindexerQuery(ReindexerQueryMethod method, ReindexerConverter reindexerConverter) {
		this.method = method;
		this.reindexerConverter = reindexerConverter;
		this.queryExecution = Lazy.of(() -> getQueryExecution(method));
	}

	@Override
	public final Object execute(Object @NonNull [] parameters) {
		ReindexerParameterAccessor parameterAccessor = new ReindexerParameterAccessor(this.method.getParameters(),
				parameters);
		ResultProcessor resultProcessor = this.method.getResultProcessor().withDynamicProjection(parameterAccessor);
		ReindexerQuery query = createQuery(parameterAccessor, resultProcessor.getReturnedType());
		Object result = this.queryExecution.get().apply(query);
		return resultProcessor.processResult(result);
	}

	@Override
	public final @NonNull QueryMethod getQueryMethod() {
		return this.method;
	}

	abstract ReindexerQuery createQuery(ReindexerParameterAccessor parameterAccessor, ReturnedType returnedType);

	Function<ReindexerQuery, Object> getQueryExecution(ReindexerQueryMethod method) {
		if (method.isSearchQuery()) {
			return getSearchQueryExecution(method);
		}
		if (method.isCollectionQuery()) {
			return (query) -> ReindexerQueryExecutions.toList(toResultAccessor(query));
		}
		if (method.isStreamQuery()) {
			return (query) -> ReindexerQueryExecutions.toStream(toResultAccessor(query));
		}
		if (method.isIteratorQuery()) {
			return this::toResultAccessor;
		}
		if (method.isPageQuery()) {
			return (query) -> {
				ReindexerResultAccessor<?> iterator = toResultAccessor(query);
				return PageableExecutionUtils.getPage(ReindexerQueryExecutions.toList(iterator),
						query.parameters().getPageable(), iterator::getTotalCount);
			};
		}
		if (method.isSliceQuery()) {
			return (query) -> ReindexerQueryExecutions.toSlice(toResultAccessor(query),
					query.parameters().getPageable());
		}
		return (query) -> ReindexerQueryExecutions.toEntity(toResultAccessor(query));
	}

	ReindexerResultAccessor<?> toResultAccessor(ReindexerQuery query) {
		return new ProjectingResultIterator<>(query.criteria(), query.returnedType(), this.reindexerConverter);
	}

	record ReindexerQuery(Query<?> criteria, ReturnedType returnedType, ReindexerParameterAccessor parameters) {
	}

	@SuppressWarnings("unchecked")
	private Function<ReindexerQuery, Object> getSearchQueryExecution(ReindexerQueryMethod method) {
		if (method.isStreamQuery()) {
			return (query) -> {
				ReindexerResultAccessor<?> it = toResultAccessor(query);
				return ReindexerQueryExecutions.toStream(it).map((e) -> new SearchResult<>(e, it.getCurrentRank()));
			};
		}
		if (method.isCollectionQuery()) {
			return (query) -> ReindexerQueryExecutions.toSearchResults(toResultAccessor(query), List.class);
		}
		return (query) -> new SearchResults<>((List<? extends SearchResult<Object>>) ReindexerQueryExecutions
			.toSearchResults(toResultAccessor(query), List.class));
	}

}
