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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ru.rt.restream.reindexer.convert.FieldConverter;
import ru.rt.restream.reindexer.convert.FieldConverterRegistry;
import ru.rt.restream.reindexer.convert.util.ConversionUtils;
import ru.rt.restream.reindexer.convert.util.ResolvableType;
import ru.rt.restream.reindexer.util.Pair;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.util.Lazy;

/**
 * Value object to capture custom conversion. {@link ReindexerCustomConversions} also acts
 * as a factory for {@link org.springframework.data.mapping.model.SimpleTypeHolder}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 * @see org.springframework.data.convert.CustomConversions
 * @see org.springframework.data.mapping.model.SimpleTypeHolder
 */
public class ReindexerCustomConversions extends CustomConversions implements ApplicationContextAware {

	private ObjectFactory<ReindexerConverter> reindexerConverter;

	/**
	 * Creates an instance with default converters.
	 */
	public ReindexerCustomConversions() {
		this(StoreConversions.NONE, Collections.emptyList());
	}

	/**
	 * Creates an instance.
	 * @param storeConversions the {@link StoreConversions} to use, must not be
	 * {@literal null}
	 * @param converters the {@link Collection} of
	 * {@link org.springframework.core.convert.converter.Converter}s to use, must not be
	 * {@literal null}
	 */
	public ReindexerCustomConversions(StoreConversions storeConversions, Collection<?> converters) {
		super(storeConversions, extendConverters(converters));
	}

	private static List<?> extendConverters(Collection<?> converters) {
		List<? super Object> result = new ArrayList<>(converters);
		result.add(MilliToLocalTimeConverter.INSTANCE);
		result.add(LocalTimeToMilliConverter.INSTANCE);
		result.add(MilliToLocalDateConverter.INSTANCE);
		result.add(LocalDateToMilliConverter.INSTANCE);
		result.add(MilliToLocalDateTimeConverter.INSTANCE);
		result.add(LocalDateTimeToMilliConverter.INSTANCE);
		return result;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.reindexerConverter = applicationContext.getBeanProvider(ReindexerConverter.class);
	}

	/**
	 * Registers a custom conversions to {@link FieldConverterRegistry}.
	 * @param registry the {@link FieldConverterRegistry} to use
	 * @param context the {@link ReindexerMappingContext} to use
	 * @since 1.5
	 */
	public void registerCustomConversions(FieldConverterRegistry registry, ReindexerMappingContext context) {
		for (ReindexerPersistentEntity<?> entity : context.getPersistentEntities()) {
			entity.doWithProperties((PropertyHandler<ReindexerPersistentProperty>) property -> {
				if (hasValueConverter(property)) {
					PropertyValueConverter<Object, Object, ValueConversionContext<ReindexerPersistentProperty>> valueConverter = getPropertyValueConversions()
						.getValueConverter(property);
					registry.registerFieldConverter(entity.getType(), property.getName(),
							new PropertyValueConverterToFieldConverterAdapter(property, valueConverter));
				}
				else {
					registry.registerFieldConverter(entity.getType(), property.getName(),
							new ConverterToFieldConverterAdapter(property));
				}
			});
		}
	}

	private final class PropertyValueConverterToFieldConverterAdapter implements FieldConverter<Object, Object> {

		private final PropertyValueConverter<Object, Object, ValueConversionContext<ReindexerPersistentProperty>> delegate;

		private final Lazy<Pair<ResolvableType, ResolvableType>> convertiblePair;

		private final Lazy<ReindexerConversionContext> context;

		private PropertyValueConverterToFieldConverterAdapter(ReindexerPersistentProperty property,
				PropertyValueConverter<Object, Object, ValueConversionContext<ReindexerPersistentProperty>> delegate) {
			this.delegate = delegate;
			this.convertiblePair = Lazy
				.of(() -> ConversionUtils.resolveConvertiblePair(delegate.getClass(), PropertyValueConverter.class));
			this.context = Lazy.of(() -> {
				ReindexerConverter reindexerConverter = ReindexerCustomConversions.this.reindexerConverter.getObject();
				return new ReindexerConversionContext(reindexerConverter, property,
						reindexerConverter.getConversionService(), ReindexerCustomConversions.this);
			});
		}

		@Override
		public Object convertToFieldType(Object dbData) {
			ReindexerConversionContext context = this.context.get();
			if (dbData == null) {
				return this.delegate.readNull(context);
			}
			return this.delegate.read(dbData, context);
		}

		@Override
		public Object convertToDatabaseType(Object field) {
			ReindexerConversionContext context = this.context.get();
			if (field == null) {
				return this.delegate.writeNull(context);
			}
			return this.delegate.write(field, context);
		}

		@Override
		public Pair<ResolvableType, ResolvableType> getConvertiblePair() {
			return this.convertiblePair.get();
		}

	}

	private final class ConverterToFieldConverterAdapter implements FieldConverter<Object, Object> {

		private final ReindexerPersistentProperty property;

		private final Lazy<Pair<ResolvableType, ResolvableType>> convertiblePair;

		private ConverterToFieldConverterAdapter(ReindexerPersistentProperty property) {
			this.property = property;
			this.convertiblePair = Lazy.of(() -> {
				ResolvableType sourceType = new ResolvableType(this.property.getType(),
						this.property.getComponentType(), this.property.isCollectionLike());
				Class<?> componentType = property.getComponentType();
				if (componentType != null) {
					componentType = getCustomWriteTarget(componentType).orElse(componentType);
				}
				Class<?> type = getCustomWriteTarget(property.getType()).orElse(property.getType());
				ResolvableType targetType = new ResolvableType(type, componentType, property.isCollectionLike());
				return new Pair<>(sourceType, targetType);
			});
		}

		@Override
		public Object convertToFieldType(Object dbData) {
			if (dbData == null) {
				return null;
			}
			if (hasCustomReadTarget(getTargetType(), getSourceType())) {
				ConversionService conversionService = ReindexerCustomConversions.this.reindexerConverter.getObject()
					.getConversionService();
				TypeDescriptor sourceTypeDescriptor = getSourceTypeDescriptor();
				if (conversionService.canConvert(getTargetTypeDescriptor(), sourceTypeDescriptor)) {
					return conversionService.convert(dbData, sourceTypeDescriptor);
				}
			}
			return dbData;
		}

		@Override
		public Object convertToDatabaseType(Object field) {
			if (field == null) {
				return null;
			}
			if (hasCustomWriteTarget(getSourceType(), getTargetType())) {
				ConversionService conversionService = ReindexerCustomConversions.this.reindexerConverter.getObject()
					.getConversionService();
				TypeDescriptor targetTypeDescriptor = getTargetTypeDescriptor();
				if (conversionService.canConvert(getSourceTypeDescriptor(), targetTypeDescriptor)) {
					return conversionService.convert(field, targetTypeDescriptor);
				}
			}
			return field;
		}

		@Override
		public Pair<ResolvableType, ResolvableType> getConvertiblePair() {
			return this.convertiblePair.get();
		}

		private Class<?> getSourceType() {
			ResolvableType sourceType = this.convertiblePair.get().getFirst();
			return getActualType(sourceType);
		}

		private Class<?> getTargetType() {
			ResolvableType targetType = this.convertiblePair.get().getSecond();
			return getActualType(targetType);
		}

		private Class<?> getActualType(ResolvableType type) {
			return type.isCollectionLike() ? type.getComponentType() : type.getType();
		}

		private TypeDescriptor getSourceTypeDescriptor() {
			ResolvableType sourceType = this.convertiblePair.get().getFirst();
			return getTypeDescriptor(sourceType);
		}

		private TypeDescriptor getTargetTypeDescriptor() {
			ResolvableType targetType = this.convertiblePair.get().getSecond();
			return getTypeDescriptor(targetType);
		}

		private TypeDescriptor getTypeDescriptor(ResolvableType type) {
			if (type.isCollectionLike()) {
				return TypeDescriptor.collection(type.getType(), TypeDescriptor.valueOf(type.getComponentType()));
			}
			return TypeDescriptor.valueOf(type.getType());
		}

	}

	@ReadingConverter
	private enum MilliToLocalTimeConverter implements Converter<Long, LocalTime> {

		INSTANCE;

		@Override
		public LocalTime convert(Long source) {
			return LocalTime.ofNanoOfDay(source * 1_000_000);
		}

	}

	@WritingConverter
	private enum LocalTimeToMilliConverter implements Converter<LocalTime, Long> {

		INSTANCE;

		@Override
		public Long convert(LocalTime source) {
			return source.toNanoOfDay() / 1_000_000;
		}

	}

	@ReadingConverter
	private enum MilliToLocalDateConverter implements Converter<Long, LocalDate> {

		INSTANCE;

		@Override
		public LocalDate convert(Long source) {
			return LocalDate.ofInstant(Instant.ofEpochMilli(source), ZoneId.systemDefault());
		}

	}

	@WritingConverter
	private enum LocalDateToMilliConverter implements Converter<LocalDate, Long> {

		INSTANCE;

		@Override
		public Long convert(LocalDate source) {
			return source.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		}

	}

	@ReadingConverter
	private enum MilliToLocalDateTimeConverter implements Converter<Long, LocalDateTime> {

		INSTANCE;

		@Override
		public LocalDateTime convert(Long source) {
			return LocalDateTime.ofInstant(Instant.ofEpochMilli(source), ZoneId.systemDefault());
		}

	}

	@WritingConverter
	private enum LocalDateTimeToMilliConverter implements Converter<LocalDateTime, Long> {

		INSTANCE;

		@Override
		public Long convert(LocalDateTime source) {
			return source.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		}

	}

}
