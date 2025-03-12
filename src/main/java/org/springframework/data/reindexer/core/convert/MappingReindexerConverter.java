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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.NamespaceOptions;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.mapping.Parameter;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.PreferredConstructorDiscoverer;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.projection.EntityProjection.PropertyProjection;
import org.springframework.data.projection.EntityProjectionIntrospector;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ReindexerConverter} that uses a {@link MappingContext} to do sophisticated mapping of domain objects to
 * target types resolving {@link NamespaceReference}s.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class MappingReindexerConverter implements ReindexerConverter {

	private static final Map<Class<?>, PreferredConstructorWrapper> cache = new ConcurrentHashMap<>();

	private final LazyLoadingProxyFactory lazyLoadingProxyFactory = new LazyLoadingProxyFactory();

	private final CustomConversions conversions = new ReindexerCustomConversions();

	private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final EntityProjectionIntrospector projectionIntrospector;

	public MappingReindexerConverter(Reindexer reindexer, ReindexerMappingContext mappingContext, ProjectionFactory projectionFactory) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.projectionIntrospector = EntityProjectionIntrospector.create(projectionFactory,
				EntityProjectionIntrospector.ProjectionPredicate.typeHierarchy()
						.and(((target, underlyingType) -> !conversions.isSimpleType(target))),
				mappingContext);
	}

	@Override
	public MappingContext<? extends ReindexerPersistentEntity<?>, ReindexerPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}

	@Override
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public EntityProjectionIntrospector getProjectionIntrospector() {
		return this.projectionIntrospector;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R, E> R project(EntityProjection<R, E> entityProjection, E entity) {
		if (!entityProjection.isProjection() || entityProjection.getMappedType().getType().isInterface()) {
			return (R) read(entityProjection.getDomainType().getType(), entity);
		}
		ReindexerPersistentEntity<?> persistentEntity = this.mappingContext.getRequiredPersistentEntity(entityProjection.getDomainType());
		PreferredConstructorWrapper constructorWrapper = cache.computeIfAbsent(entityProjection.getMappedType().getType(), (type) -> {
			PreferredConstructor<?, ?> preferredConstructor = PreferredConstructorDiscoverer.discover(type);
			Assert.state(preferredConstructor != null, () -> "No preferred constructor found for " + type);
			return new PreferredConstructorWrapper(preferredConstructor);
		});
		int i = 0;
		Object[] values = new Object[constructorWrapper.preferredConstructor.getParameterCount()];
		for (PropertyProjection<?, ?> propertyProjection : entityProjection) {
			String propertyName = propertyProjection.getPropertyPath().getLeafProperty().getSegment();
			if (!constructorWrapper.hasParameter(propertyName)) {
				continue;
			}
			ReindexerPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty(propertyName);
			if (persistentProperty.isNamespaceReference()) {
				NamespaceReference namespaceReference = persistentProperty.getNamespaceReference();
				Class<?> referenceType = propertyProjection.getMappedType().getRequiredActualType().getType();
				EntityProjection<Object, Object> referenceProjection = (EntityProjection<Object, Object>) this.projectionIntrospector
						.introspect(referenceType, propertyProjection.getDomainType().getRequiredActualType().getType());
				if (namespaceReference.lazy()) {
					Object proxy = createProxyIfNeeded(namespaceReference, referenceType, persistentProperty, entity,
							referenceValue -> {
								if (referenceValue instanceof Iterable<?> referenceEntities) {
									List<Object> projectionEntities = new ArrayList<>();
									for (Object projectionEntity : referenceEntities) {
										projectionEntities.add(project(referenceProjection, projectionEntity));
									}
									return projectionEntities;
								}
								return project(referenceProjection, referenceValue);
							});
					values[i++] = proxy;
				}
				else {
					Object referenceValue = BeanPropertyUtils.getProperty(entity, propertyName);
					if (referenceValue != null) {
						if (referenceValue instanceof Iterable<?> referenceEntities) {
							List<Object> projectionEntities = new ArrayList<>();
							for (Object referenceEntity : referenceEntities) {
								projectionEntities.add(project(referenceProjection, referenceEntity));
							}
							referenceValue = projectionEntities;
						}
						else {
							referenceValue = project(referenceProjection, referenceValue);
						}
					}
					values[i++] = referenceValue;
				}
			}
			else {
				values[i++] = BeanPropertyUtils.getProperty(entity, propertyName);
			}
		}
		try {
			return (R) constructorWrapper.preferredConstructor.getConstructor().newInstance(values);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R> R read(Class<R> type, Object source) {
		readProperties(type, source);
		return (R) source;
	}

	private void readProperties(Class<?> type, Object source) {
		ReindexerPersistentEntity<?> persistentEntity = this.mappingContext.getRequiredPersistentEntity(type);
		for (ReindexerPersistentProperty property : persistentEntity.getPersistentProperties(NamespaceReference.class)) {
			NamespaceReference namespaceReference = property.getNamespaceReference();
			if (namespaceReference.lazy()) {
				Object proxy = createProxyIfNeeded(namespaceReference, property.getType(), property, source,
						referenceValue -> {
							if (referenceValue instanceof Iterable<?> referenceEntities) {
								for (Object referenceEntity : referenceEntities) {
									readProperties(property.getActualType(), referenceEntity);
								}
							}
							else {
								readProperties(property.getType(), referenceValue);
							}
							return referenceValue;
						});
				if (proxy != null) {
					BeanPropertyUtils.setProperty(source, property.getName(), proxy);
				}
			}
			else {
				Object referenceEntity = BeanPropertyUtils.getProperty(source, property.getName());
				if (referenceEntity != null) {
					if (referenceEntity instanceof Iterable<?> values) {
						for (Object value : values) {
							readProperties(property.getActualType(), value);
						}
					}
					else {
						readProperties(property.getActualType(), referenceEntity);
					}
				}
			}
		}
	}

	private Object createProxyIfNeeded(NamespaceReference namespaceReference, Class<?> type, ReindexerPersistentProperty property, Object entity, Converter<Object, ?> valueConverter) {
		Object source = BeanPropertyUtils.getProperty(entity, namespaceReference.indexName());
		if (source == null) {
			return null;
		}
		ReindexerPersistentEntity<?> referenceEntity = this.mappingContext.getRequiredPersistentEntity(property);
		String namespaceName = StringUtils.hasText(namespaceReference.namespace()) ? namespaceReference.namespace()
				: referenceEntity.getNamespace();
		Supplier<Object> callback = () -> {
			Namespace<?> namespace = this.reindexer.openNamespace(namespaceName, NamespaceOptions.defaultOptions(), referenceEntity.getType());
			String indexName = referenceEntity.getRequiredIdProperty().getName();
			return property.isCollectionLike() ? namespace.query().where(indexName, Condition.SET, (Collection<?>) source).toList()
					: namespace.query().where(indexName, Condition.EQ, source).getOne();
		};
		return this.lazyLoadingProxyFactory.createLazyLoadingProxy(type, property, callback, new NamespaceReferenceSource(namespaceName, source), valueConverter);
	}

	@Override
	public void write(Object source, Object sink) {
		// NOOP
	}

	private static final class PreferredConstructorWrapper {

		private final PreferredConstructor<?, ?> preferredConstructor;

		private final Set<String> parameterNames;

		private PreferredConstructorWrapper(PreferredConstructor<?, ?> preferredConstructor) {
			this.preferredConstructor = preferredConstructor;
			this.parameterNames = preferredConstructor.getParameters().stream()
					.map(Parameter::getName)
					.collect(Collectors.toSet());
		}

		private boolean hasParameter(String name) {
			return this.parameterNames.contains(name);
		}

	}

}
