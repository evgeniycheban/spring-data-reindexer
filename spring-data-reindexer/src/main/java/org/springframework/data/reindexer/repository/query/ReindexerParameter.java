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

import ru.rt.restream.reindexer.vector.params.KnnSearchParam;

import org.springframework.core.MethodParameter;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Vector;
import org.springframework.data.repository.query.Parameter;

/**
 * A Reindexer-specific {@link Parameter}.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class ReindexerParameter extends Parameter {

	private static final List<Class<?>> TYPES = List.of(Vector.class, KnnSearchParam.class);

	/**
	 * Creates an instance.
	 * @param parameter the {@link MethodParameter} to use
	 * @param domainType the domain type to use
	 */
	public ReindexerParameter(MethodParameter parameter, TypeInformation<?> domainType) {
		super(parameter, domainType);
	}

	/**
	 * Returns {@literal true} if this parameter is a type of
	 * {@link ru.rt.restream.reindexer.vector.params.KnnSearchParam}.
	 * @return {@literal true} if this parameter is a type of {@code KnnSearchParam}
	 */
	public boolean isKnnSearchParam() {
		return KnnSearchParam.class.isAssignableFrom(getType());
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Extends the base implementation with {@link #TYPES}.
	 */
	@Override
	public boolean isSpecialParameter() {
		for (Class<?> specialParameterType : TYPES) {
			if (specialParameterType.isAssignableFrom(getType())) {
				return true;
			}
		}
		return super.isSpecialParameter();
	}

}
