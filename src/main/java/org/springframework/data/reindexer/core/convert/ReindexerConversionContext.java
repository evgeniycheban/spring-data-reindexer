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
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;

/**
 * {@link ValueConversionContext} that allows to delegate read/write to an underlying
 * {@link ReindexerConverter}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class ReindexerConversionContext implements ValueConversionContext<ReindexerPersistentProperty> {

	private final ReindexerConverter reindexerConverter;

	private final ReindexerPersistentProperty property;

	private final ConversionService conversionService;

	private final CustomConversions conversions;

	/**
	 * Creates an instance.
	 * @param reindexerConverter the {@link ReindexerConverter} to use, must not be
	 * {@literal null}
	 * @param property the {@link PersistentProperty} to use, must not be {@literal null}
	 * @param conversionService the {@link ConversionService} to use, must not be
	 * {@literal null}
	 * @param conversions the {@link CustomConversions} to use, must not be
	 * {@literal null}
	 */
	public ReindexerConversionContext(ReindexerConverter reindexerConverter, ReindexerPersistentProperty property,
			ConversionService conversionService, CustomConversions conversions) {
		Assert.notNull(reindexerConverter, "reindexerConverter must not be null");
		Assert.notNull(property, "property must not be null");
		Assert.notNull(conversionService, "conversionService must not be null");
		Assert.notNull(conversions, "conversions must not be null");
		this.reindexerConverter = reindexerConverter;
		this.property = property;
		this.conversionService = conversionService;
		this.conversions = conversions;
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
		if (this.conversions.hasCustomReadTarget(this.property.getActualType(),
				target.getRequiredActualType().getType())) {
			TypeDescriptor targetType = getTypeDescriptor(target);
			if (this.conversionService.canConvert(getTypeDescriptor(this.property.getTypeInformation()), targetType)) {
				return (T) this.conversionService.convert(value, targetType);
			}
		}
		if (this.property.isEntity()) {
			return (T) readEntity(value, target);
		}
		if (this.conversions.isSimpleType(target.getType())) {
			return (T) value;
		}
		return ValueConversionContext.super.read(value, target);
	}

	private TypeDescriptor getTypeDescriptor(TypeInformation<?> typeInformation) {
		if (typeInformation.isCollectionLike()) {
			return TypeDescriptor.collection(typeInformation.getType(),
					TypeDescriptor.valueOf(typeInformation.getRequiredActualType().getType()));
		}
		return TypeDescriptor.valueOf(typeInformation.getType());
	}

	@SuppressWarnings("unchecked")
	private Object readEntity(Object value, TypeInformation<?> target) {
		EntityProjection<Object, Object> projection = (EntityProjection<Object, Object>) this.reindexerConverter
			.getProjectionIntrospector()
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
