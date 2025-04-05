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

import java.util.Collection;
import java.util.function.Supplier;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.NamespaceOptions;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.PropertyValueConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.CachingValueExpressionEvaluatorFactory;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.mapping.model.PersistentEntityParameterValueProvider;
import org.springframework.data.mapping.model.PropertyValueProvider;
import org.springframework.data.mapping.model.SpELContext;
import org.springframework.data.mapping.model.ValueExpressionEvaluator;
import org.springframework.data.mapping.model.ValueExpressionParameterValueProvider;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.projection.EntityProjectionIntrospector;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ReindexerConverter} that uses a {@link MappingContext} to do sophisticated mapping of domain objects to
 * target types resolving {@link NamespaceReference}s.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class MappingReindexerConverter implements ReindexerConverter, ApplicationContextAware, InitializingBean, EnvironmentCapable {

	private final LazyLoadingProxyFactory lazyLoadingProxyFactory = new LazyLoadingProxyFactory();

	private final GenericConversionService conversionService = new DefaultConversionService();

	private final SpelExpressionParser expressionParser = new SpelExpressionParser();

	private final SpELContext spELContext = new SpELContext(this.expressionParser, new ReflectivePropertyAccessor());

	private final CachingValueExpressionEvaluatorFactory expressionEvaluatorFactory = new CachingValueExpressionEvaluatorFactory(
			this.expressionParser, this, this.spELContext::getEvaluationContext);

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory(this.expressionParser);

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final EntityProjectionIntrospector projectionIntrospector;

	private CustomConversions conversions = new ReindexerCustomConversions();

	private EntityInstantiators instantiators = new EntityInstantiators();

	private Environment environment;

	/**
	 * Creates an instance.
	 *
	 * @param reindexer the {@link Reindexer} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 */
	public MappingReindexerConverter(Reindexer reindexer, ReindexerMappingContext mappingContext) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.projectionIntrospector = EntityProjectionIntrospector.create(this.projectionFactory,
				EntityProjectionIntrospector.ProjectionPredicate.typeHierarchy()
						.and(((target, underlyingType) -> !conversions.isSimpleType(target))),
				mappingContext);
	}

	@Override
	public ReindexerMappingContext getMappingContext() {
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

	/**
	 * Sets a {@link CustomConversions} to use.
	 *
	 * @param conversions the {@link CustomConversions} to use
	 */
	public void setConversions(CustomConversions conversions) {
		Assert.notNull(conversions, "conversions must not be null");
		this.conversions = conversions;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R, E> R project(EntityProjection<R, E> entityProjection, E entity) {
		if (!entityProjection.isProjection()) {
			return (R) read(entityProjection.getDomainType().getType(), entity);
		}
		if (entityProjection.getMappedType().getType().isInterface()) {
			return this.projectionFactory.createProjection(entityProjection.getMappedType().getType(), read(entityProjection.getDomainType().getType(), entity));
		}
		ReindexerPersistentEntity<?> domainEntity = this.mappingContext.getRequiredPersistentEntity(entityProjection.getDomainType());
		PersistentPropertyAccessor<E> domainAccessor = new ConvertingPropertyAccessor<>(domainEntity.getPropertyAccessor(entity),
				this.conversionService);
		ReindexerPersistentEntity<?> mappedEntity = this.mappingContext.getRequiredPersistentEntity(entityProjection.getMappedType());
		EntityInstantiator instantiator = this.instantiators.getInstantiatorFor(mappedEntity);
		ValueExpressionEvaluator evaluator = this.expressionEvaluatorFactory.create(domainAccessor.getBean());
		ReindexerPropertyValueProvider valueProvider = new ReindexerPropertyValueProvider(domainEntity, domainAccessor, evaluator);
		Object instance = instantiator.createInstance(mappedEntity, getParameterProvider(mappedEntity, valueProvider));
		PersistentPropertyAccessor<?> mappedAccessor = new ConvertingPropertyAccessor<>(mappedEntity.getPropertyAccessor(instance),
				this.conversionService);
		if (mappedEntity.requiresPropertyPopulation()) {
			readProperties(mappedEntity, mappedAccessor, valueProvider);
		}
		return (R) mappedAccessor.getBean();
	}

	private ParameterValueProvider<ReindexerPersistentProperty> getParameterProvider(ReindexerPersistentEntity<?> entity,
			ReindexerPropertyValueProvider valueProvider) {
		ValueExpressionEvaluator evaluator = this.expressionEvaluatorFactory.create(valueProvider.accessor.getBean());
		PersistentEntityParameterValueProvider<ReindexerPersistentProperty> parameterProvider = new PersistentEntityParameterValueProvider<>(
				entity, valueProvider, null);
		return new ValueExpressionParameterValueProvider<>(evaluator, this.conversionService, parameterProvider);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R> R read(Class<R> type, Object source) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(type);
		PersistentPropertyAccessor<?> accessor = new ConvertingPropertyAccessor<>(entity.getPropertyAccessor(source), this.conversionService);
		ValueExpressionEvaluator evaluator = this.expressionEvaluatorFactory.create(source);
		ReindexerPropertyValueProvider valueProvider = new ReindexerPropertyValueProvider(entity, accessor, evaluator);
		readProperties(entity, accessor, valueProvider);
		return (R) source;
	}

	private void readProperties(ReindexerPersistentEntity<?> entity, PersistentPropertyAccessor<?> accessor, ReindexerPropertyValueProvider valueProvider) {
		for (ReindexerPersistentProperty property : entity) {
			if (!entity.isCreatorArgument(property) && property.isReadable()) {
				accessor.setProperty(property, valueProvider.getPropertyValue(property));
			}
		}
	}

	@Override
	public void write(Object source, Object sink) {
		// NOOP
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.projectionFactory.setBeanFactory(applicationContext);
		this.projectionFactory.setBeanClassLoader(applicationContext.getClassLoader());
		this.environment = applicationContext.getEnvironment();
	}

	@Override
	public void afterPropertiesSet() {
		this.conversions.registerConvertersIn(this.conversionService);
	}

	@Override
	public Environment getEnvironment() {
		return this.environment;
	}

	private final class ReindexerPropertyValueProvider implements PropertyValueProvider<ReindexerPersistentProperty> {

		private final ReindexerPersistentEntity<?> entity;

		private final PersistentPropertyAccessor<?> accessor;

		private final ValueExpressionEvaluator evaluator;

		private ReindexerPropertyValueProvider(ReindexerPersistentEntity<?> entity, PersistentPropertyAccessor<?> accessor, ValueExpressionEvaluator evaluator) {
			this.entity = entity;
			this.accessor = accessor;
			this.evaluator = evaluator;
		}

		@Override
		public <T> T getPropertyValue(ReindexerPersistentProperty targetProperty) {
			ReindexerPersistentProperty sourceProperty = this.entity.getRequiredPersistentProperty(targetProperty.getName());
			if (sourceProperty.isNamespaceReference()) {
				return readNamespaceReference(sourceProperty, targetProperty);
			}
			String expression = targetProperty.getSpelExpression();
			Object value = expression != null ? this.evaluator.evaluate(expression) : this.accessor.getProperty(targetProperty);
			return readPropertyValue(sourceProperty, targetProperty, value);
		}

		@SuppressWarnings("unchecked")
		private <T> T readNamespaceReference(ReindexerPersistentProperty sourceProperty, ReindexerPersistentProperty targetProperty) {
			Object value = this.accessor.getProperty(sourceProperty);
			if (ObjectUtils.isEmpty(value)) {
				NamespaceReference namespaceReference = sourceProperty.getNamespaceReference();
				if (namespaceReference.lazy() || namespaceReference.fetch()) {
					Object source = this.accessor.getProperty(this.entity.getRequiredPersistentProperty(namespaceReference.indexName()));
					if (ObjectUtils.isEmpty(source)) {
						return (T) value;
					}
					return (T) createProxyIfNeeded(namespaceReference, source, sourceProperty, targetProperty,
							resolvedReference -> readPropertyValue(sourceProperty, targetProperty, resolvedReference));
				}
			}
			return readPropertyValue(sourceProperty, targetProperty, value);
		}

		private Object createProxyIfNeeded(NamespaceReference namespaceReference, Object source,
				ReindexerPersistentProperty sourceProperty, ReindexerPersistentProperty targetProperty,
				Converter<Object, ?> valueConverter) {
			ReindexerPersistentEntity<?> referenceEntity = MappingReindexerConverter.this.mappingContext.getRequiredPersistentEntity(sourceProperty);
			String namespaceName = StringUtils.hasText(namespaceReference.namespace()) ? namespaceReference.namespace()
					: referenceEntity.getNamespace();
			Supplier<Object> callback = () -> {
				Namespace<?> namespace = MappingReindexerConverter.this.reindexer.openNamespace(namespaceName, NamespaceOptions.defaultOptions(), referenceEntity.getType());
				String indexName = referenceEntity.getRequiredIdProperty().getName();
				if (source instanceof Collection<?> values) {
					return namespace.query().where(indexName, Condition.SET, values).toList();
				}
				return namespace.query().where(indexName, Condition.EQ, source).findOne().orElse(null);
			};
			return MappingReindexerConverter.this.lazyLoadingProxyFactory.createLazyLoadingProxy(targetProperty.getType(), targetProperty, callback, new NamespaceReferenceSource(namespaceName, source), valueConverter);
		}

		@SuppressWarnings("unchecked")
		private <T> T readPropertyValue(ReindexerPersistentProperty sourceProperty, ReindexerPersistentProperty targetProperty, Object value) {
			ReindexerConversionContext conversionContext = new ReindexerConversionContext(MappingReindexerConverter.this,
					sourceProperty, MappingReindexerConverter.this.conversionService, MappingReindexerConverter.this.conversions);
			PropertyValueConversions valueConversions = MappingReindexerConverter.this.conversions.getPropertyValueConversions();
			if (valueConversions != null && valueConversions.hasValueConverter(targetProperty)) {
				PropertyValueConverter<Object, Object, ValueConversionContext<ReindexerPersistentProperty>> valueConverter = valueConversions
						.getValueConverter(targetProperty);
				return (T) (value != null ? valueConverter.read(value, conversionContext) : valueConverter.readNull(conversionContext));
			}
			return (T) conversionContext.read(value, targetProperty.getTypeInformation());
		}

	}

}
