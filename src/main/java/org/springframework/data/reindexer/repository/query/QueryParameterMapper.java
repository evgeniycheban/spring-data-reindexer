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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.ReindexerIndex;

import org.springframework.core.convert.ConversionService;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.reindexer.core.convert.ReindexerConversionContext;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public final class QueryParameterMapper {

	private final Class<?> domainType;

	private final Map<String, ReindexerIndex> mappedIndexes;

	private final ReindexerMappingContext mappingContext;

	private final ReindexerConverter reindexerConverter;

	/**
	 * Creates an instance.
	 * @param domainType the domain type to use
	 * @param mappedIndexes the mapped indexes to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public QueryParameterMapper(Class<?> domainType, Map<String, ReindexerIndex> mappedIndexes,
			ReindexerMappingContext mappingContext, ReindexerConverter reindexerConverter) {
		this.domainType = domainType;
		this.mappedIndexes = mappedIndexes;
		this.mappingContext = mappingContext;
		this.reindexerConverter = reindexerConverter;
	}

	/**
	 * Maps an array of values for the given index name. Considers custom conversions and
	 * internal Reindexer index definitions.
	 * @param indexName the index name to use
	 * @param values the values to use
	 * @return the mapped values to use
	 */
	public Object[] mapParameterValues(String indexName, Object... values) {
		Object[] result = new Object[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = mapParameterValue(indexName, values[i]);
		}
		return result;
	}

	/**
	 * Maps a single value for the given index name. Considers custom conversions and
	 * internal Reindexer index definitions.
	 * @param indexName the index name to use
	 * @param value the value to use
	 * @return the mapped value to use
	 */
	public Object mapParameterValue(String indexName, Object value) {
		if (value == null) {
			return null;
		}
		PersistentPropertyPath<ReindexerPersistentProperty> propertyPath = this.mappingContext
			.getPersistentPropertyPath(indexName, this.domainType);
		ReindexerPersistentProperty property = propertyPath.getLeafProperty();
		CustomConversions conversions = this.reindexerConverter.getCustomConversions();
		if (conversions.hasValueConverter(property)) {
			ReindexerConversionContext conversionContext = new ReindexerConversionContext(this.reindexerConverter,
					property, this.reindexerConverter.getConversionService(), conversions);
			PropertyValueConverter<Object, ?, ValueConversionContext<ReindexerPersistentProperty>> valueConverter = conversions
				.getPropertyValueConversions()
				.getValueConverter(property);
			return valueConverter.write(value, conversionContext);
		}
		if (conversions.hasCustomWriteTarget(value.getClass())) {
			Class<?> customTarget = conversions.getCustomWriteTarget(property.getActualType()).get();
			ConversionService conversionService = this.reindexerConverter.getConversionService();
			if (conversionService.canConvert(value.getClass(), customTarget)) {
				return conversionService.convert(value, customTarget);
			}
		}
		if (value instanceof Enum<?>) {
			ReindexerIndex index = this.mappedIndexes.get(indexName);
			Assert.notNull(index, () -> "Index not found: " + indexName);
			if (index.getFieldType() == FieldType.STRING) {
				return ((Enum<?>) value).name();
			}
			return ((Enum<?>) value).ordinal();
		}
		if (value instanceof Collection<?> values) {
			List<Object> result = new ArrayList<>(values.size());
			for (Object object : values) {
				result.add(mapParameterValue(indexName, object));
			}
			return result;
		}
		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			List<Object> result = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				result.add(mapParameterValue(indexName, Array.get(value, i)));
			}
			return result;
		}
		return value;
	}

}
