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
package org.springframework.data.reindexer.repository.support;

import java.util.stream.Stream;

import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Slice;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.repository.util.QueryExecutionConverters;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerQueryExecutionConverters {

	private ReindexerQueryExecutionConverters() {
	}

	public static TypeInformation<?> unwrapWrapperTypes(TypeInformation<?> type, TypeInformation<?> reference) {
		Assert.notNull(type, "type must not be null");
		if (reference.isAssignableFrom(type)) {
			return type;
		}
		Class<?> rawType = type.getType();
		boolean needToUnwrap = type.isCollectionLike() || Slice.class.isAssignableFrom(rawType)
				|| GeoResults.class.isAssignableFrom(rawType) || rawType.isArray()
				|| QueryExecutionConverters.supports(rawType) || Stream.class.isAssignableFrom(rawType)
				|| ResultIterator.class.isAssignableFrom(rawType);
		return needToUnwrap ? unwrapWrapperTypes(type.getRequiredComponentType(), reference) : type;
	}

}
