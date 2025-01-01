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
package org.springframework.data.reindexer.repository.util;

import java.util.stream.Stream;

import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.domain.Slice;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.repository.util.QueryExecutionConverters;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;

/**
 * Converters to potentially wrap the execution of a repository method into a variety of wrapper types
 * potentially being available on the classpath.
 * @see QueryExecutionConverters
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerQueryExecutionConverters {

	private ReindexerQueryExecutionConverters() {
	}

	/**
	 * Recursively unwraps well known wrapper types from the given {@link TypeInformation} but aborts at the given
	 * reference type.
	 * This method is a copy of {@link QueryExecutionConverters#unwrapWrapperTypes(TypeInformation, TypeInformation)}
	 * with extension of adding {@link ResultIterator} type check.
	 *
	 * @param type must not be {@literal null}.
	 * @param reference must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static TypeInformation<?> unwrapWrapperTypes(TypeInformation<?> type, TypeInformation<?> reference) {
		Assert.notNull(type, "type must not be null");
		if (reference.isAssignableFrom(type)) {
			return type;
		}
		Class<?> rawType = type.getType();
		boolean needToUnwrap = type.isCollectionLike()
				|| Slice.class.isAssignableFrom(rawType)
				|| GeoResults.class.isAssignableFrom(rawType)
				|| rawType.isArray()
				|| QueryExecutionConverters.supports(rawType)
				|| Stream.class.isAssignableFrom(rawType)
				|| ResultIterator.class.isAssignableFrom(rawType);
		return needToUnwrap ? unwrapWrapperTypes(type.getRequiredComponentType(), reference) : type;
	}

}
