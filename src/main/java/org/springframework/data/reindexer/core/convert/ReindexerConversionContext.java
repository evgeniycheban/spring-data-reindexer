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
package org.springframework.data.reindexer.core.convert;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.ConversionService;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.util.TypeInformation;

/**
 * {@link ValueConversionContext} that allows to delegate read/write to an underlying {@link ReindexerConverter}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class ReindexerConversionContext implements ValueConversionContext<ReindexerPersistentProperty> {

	private final ReindexerConverter reindexerConverter;

	private final ReindexerPersistentProperty property;

	private final ConversionService conversionService;

	public ReindexerConversionContext(ReindexerConverter reindexerConverter,
			ReindexerPersistentProperty property, ConversionService conversionService) {
		this.reindexerConverter = reindexerConverter;
		this.property = property;
		this.conversionService = conversionService;
	}

	@Override
	public ReindexerPersistentProperty getProperty() {
		return this.property;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T read(Object value, TypeInformation<T> target) {
		if (value == null) {
			return null;
		}
		if (this.property.isNamespaceReference()) {
			return (T) readNamespaceReference(value, target);
		}
		return ValueConversionContext.super.read(value, target);
	}

	@SuppressWarnings("unchecked")
	private Object readNamespaceReference(Object value, TypeInformation<?> target) {
		EntityProjection<Object, Object> projection = (EntityProjection<Object, Object>) this.reindexerConverter.getProjectionIntrospector()
				.introspect(target.getRequiredActualType().getType(), this.property.getActualType());
		if (value instanceof Iterable<?> referenceEntities) {
			List<Object> projectionEntities = new ArrayList<>();
			for (Object projectionEntity : referenceEntities) {
				projectionEntities.add(this.reindexerConverter.project(projection, projectionEntity));
			}
			if (this.conversionService.canConvert(this.property.getType(), target.getType())) {
				return this.conversionService.convert(projectionEntities, target.getType());
			}
			return projectionEntities;
		}
		return this.reindexerConverter.project(projection, value);
	}

}
