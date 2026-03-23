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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersSource;

/**
 * A Reindexer-specific {@link Parameters}.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class ReindexerParameters extends Parameters<ReindexerParameters, ReindexerParameter> {

	private final int knnSearchParamIndex;

	private final Map<String, ReindexerParameter> namedParameters;

	public ReindexerParameters(ParametersSource source) {
		super(source, (parameter) -> new ReindexerParameter(parameter, source.getDomainTypeInformation()));
		Map<String, ReindexerParameter> namedParameters = new LinkedHashMap<>();
		int knnSearchParamIndex = -1;
		for (ReindexerParameter parameter : this) {
			parameter.getName().ifPresent((name) -> namedParameters.put(name, parameter));
			if (parameter.isKnnSearchParam()) {
				knnSearchParamIndex = parameter.getIndex();
			}
		}
		this.namedParameters = Collections.unmodifiableMap(namedParameters);
		this.knnSearchParamIndex = knnSearchParamIndex;
	}

	private ReindexerParameters(List<ReindexerParameter> originals) {
		super(originals);
		Map<String, ReindexerParameter> namedParameters = new LinkedHashMap<>();
		int knnSearchParamIndex = -1;
		for (ReindexerParameter original : originals) {
			original.getName().ifPresent((name) -> namedParameters.put(name, original));
			if (original.isKnnSearchParam()) {
				knnSearchParamIndex = original.getIndex();
			}
		}
		this.namedParameters = Collections.unmodifiableMap(namedParameters);
		this.knnSearchParamIndex = knnSearchParamIndex;
	}

	/**
	 * Returns {@literal true} if
	 * {@link ru.rt.restream.reindexer.vector.params.KnnSearchParam} is present in the
	 * parameters.
	 * @return {@literal true} if {@code KnnSearchParam} is present in the parameters
	 */
	public boolean hasKnnSearchParam() {
		return this.knnSearchParamIndex != -1;
	}

	/**
	 * Returns an index of {@link ru.rt.restream.reindexer.vector.params.KnnSearchParam}
	 * parameter.
	 * @return the index of {@code KnnSearchParam} parameter to use
	 */
	public int getKnnSearchParamIndex() {
		return this.knnSearchParamIndex;
	}

	/**
	 * Returns a {@link ReindexerParameter} for the given {@code name}.
	 * @return a {@code ReindexerParameter} for the given {@code name}
	 */
	public ReindexerParameter getParameter(String name) {
		return this.namedParameters.get(name);
	}

	@Override
	protected ReindexerParameters createFrom(@NonNull List<ReindexerParameter> parameters) {
		return new ReindexerParameters(parameters);
	}

}
