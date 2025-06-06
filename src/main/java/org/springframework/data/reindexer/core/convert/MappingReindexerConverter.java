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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.convert.PropertyValueConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.MappingContext;
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
import org.springframework.data.reindexer.repository.support.TransactionalNamespace;
import org.springframework.data.reindexer.repository.util.QueryUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ReindexerConverter} that uses a {@link MappingContext} to do sophisticated
 * mapping of domain objects to target types resolving {@link NamespaceReference}s.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class MappingReindexerConverter
		implements ReindexerConverter, ApplicationContextAware, InitializingBean, EnvironmentCapable {

	private final LazyLoadingProxyFactory lazyLoadingProxyFactory = new LazyLoadingProxyFactory();

	private final GenericConversionService conversionService = new DefaultConversionService();

	private final SpelExpressionParser expressionParser = new SpelExpressionParser();

	private SpELContext spELContext = new SpELContext(this.expressionParser, new BeanPropertyAccessor());

	private final ReindexerExpressionEvaluatorFactory expressionEvaluatorFactory = new ReindexerExpressionEvaluatorFactory(
			this.expressionParser, this, o -> this.spELContext.getEvaluationContext(o));

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory(
			this.expressionParser);

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final EntityProjectionIntrospector projectionIntrospector;

	private ReindexerCustomConversions conversions = new ReindexerCustomConversions();

	private EntityInstantiators instantiators = new EntityInstantiators();

	private Environment environment;

	/**
	 * Creates an instance.
	 * @param reindexer the {@link Reindexer} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 */
	public MappingReindexerConverter(Reindexer reindexer, ReindexerMappingContext mappingContext) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.projectionIntrospector = EntityProjectionIntrospector.create(this.projectionFactory,
				EntityProjectionIntrospector.ProjectionPredicate.typeHierarchy()
					.and(((target, underlyingType) -> !this.conversions.isSimpleType(target))),
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

	@Override
	public ReindexerCustomConversions getCustomConversions() {
		return this.conversions;
	}

	/**
	 * Sets a {@link ReindexerCustomConversions} to use.
	 * @param conversions the {@link ReindexerCustomConversions} to use
	 */
	public void setConversions(ReindexerCustomConversions conversions) {
		Assert.notNull(conversions, "conversions must not be null");
		this.conversions = conversions;
	}

	/**
	 * Registers {@link EntityInstantiators} to customize entity instantiation.
	 * @param instantiators can be {@literal null}. Uses default
	 * {@link EntityInstantiators} if so.
	 */
	public void setInstantiators(EntityInstantiators instantiators) {
		this.instantiators = instantiators == null ? new EntityInstantiators() : instantiators;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R, E> R project(EntityProjection<R, E> entityProjection, E entity) {
		if (!entityProjection.isProjection()) {
			return (R) read(entityProjection.getDomainType().getType(), entity);
		}
		if (entityProjection.getMappedType().getType().isInterface()) {
			return this.projectionFactory.createProjection(entityProjection.getMappedType().getType(),
					read(entityProjection.getDomainType().getType(), entity));
		}
		ReindexerPersistentEntity<?> domainEntity = this.mappingContext
			.getRequiredPersistentEntity(entityProjection.getDomainType());
		PersistentPropertyAccessor<E> domainAccessor = domainEntity.getPropertyAccessor(entity);
		ReindexerPersistentEntity<?> mappedEntity = this.mappingContext
			.getRequiredPersistentEntity(entityProjection.getMappedType());
		EntityInstantiator instantiator = this.instantiators.getInstantiatorFor(mappedEntity);
		ReindexerPropertyValueProvider valueProvider = new ReindexerPropertyValueProvider(domainEntity, domainAccessor);
		Object instance = instantiator.createInstance(mappedEntity, getParameterProvider(mappedEntity, valueProvider));
		PersistentPropertyAccessor<?> mappedAccessor = mappedEntity.getPropertyAccessor(instance);
		if (mappedEntity.requiresPropertyPopulation()) {
			populateProperties(mappedEntity, mappedAccessor, valueProvider);
		}
		return (R) mappedAccessor.getBean();
	}

	private ParameterValueProvider<ReindexerPersistentProperty> getParameterProvider(
			ReindexerPersistentEntity<?> entity, ReindexerPropertyValueProvider valueProvider) {
		ValueExpressionEvaluator evaluator = this.expressionEvaluatorFactory.create(valueProvider.accessor.getBean());
		PersistentEntityParameterValueProvider<ReindexerPersistentProperty> parameterProvider = new PersistentEntityParameterValueProvider<>(
				entity, valueProvider, null);
		return new ValueExpressionParameterValueProvider<>(evaluator, this.conversionService, parameterProvider);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R> R read(Class<R> type, Object source) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(type);
		PersistentPropertyAccessor<?> accessor = entity.getPropertyAccessor(source);
		ReindexerPropertyValueProvider valueProvider = new ReindexerPropertyValueProvider(entity, accessor);
		populateProperties(entity, accessor, valueProvider);
		return (R) accessor.getBean();
	}

	private void populateProperties(ReindexerPersistentEntity<?> entity, PersistentPropertyAccessor<?> accessor,
			ReindexerPropertyValueProvider valueProvider) {
		for (ReindexerPersistentProperty property : entity) {
			if (!entity.isCreatorArgument(property) && property.isReadable()) {
				accessor.setProperty(property, valueProvider.getPropertyValue(property));
			}
		}
	}

	@Override
	public void write(Object source, Object sink) {
		throw new InvalidDataAccessApiUsageException(
				"Writing converters are applied during serialization in reindexer-java connector since 1.27 version");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.projectionFactory.setBeanFactory(applicationContext);
		this.projectionFactory.setBeanClassLoader(applicationContext.getClassLoader());
		this.environment = applicationContext.getEnvironment();
		this.spELContext = new SpELContext(this.spELContext, applicationContext);
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

		private final ReindexerExpressionEvaluator evaluator;

		private ReindexerPropertyValueProvider(ReindexerPersistentEntity<?> entity,
				PersistentPropertyAccessor<?> accessor) {
			this.entity = entity;
			this.accessor = accessor;
			this.evaluator = MappingReindexerConverter.this.expressionEvaluatorFactory.create(accessor.getBean());
		}

		@Override
		public <T> T getPropertyValue(ReindexerPersistentProperty targetProperty) {
			ReindexerPersistentProperty sourceProperty = this.entity.getPersistentProperty(targetProperty.getName());
			if (sourceProperty == null) {
				// in case the target property is calculated using @Value annotation.
				sourceProperty = targetProperty;
			}
			if (sourceProperty.isNamespaceReference()) {
				return readNamespaceReference(sourceProperty, targetProperty);
			}
			String expression = targetProperty.getSpelExpression();
			Object value = expression != null ? this.evaluator.evaluate(expression)
					: this.accessor.getProperty(sourceProperty);
			return readPropertyValue(sourceProperty, targetProperty, value);
		}

		@SuppressWarnings("unchecked")
		private <T> T readNamespaceReference(ReindexerPersistentProperty sourceProperty,
				ReindexerPersistentProperty targetProperty) {
			Object value = this.accessor.getProperty(sourceProperty);
			if (ObjectUtils.isEmpty(value)) {
				NamespaceReference namespaceReference = sourceProperty.getNamespaceReference();
				if (shouldCreateProxy(namespaceReference)) {
					Object proxy = createProxyIfNeeded(namespaceReference, sourceProperty, targetProperty);
					return (T) (proxy != null ? (T) proxy : value);
				}
			}
			return readPropertyValue(sourceProperty, targetProperty, value);
		}

		private boolean shouldCreateProxy(NamespaceReference namespaceReference) {
			return namespaceReference.lazy() || namespaceReference.fetch()
					|| StringUtils.hasText(namespaceReference.lookup());
		}

		private Object createProxyIfNeeded(NamespaceReference namespaceReference,
				ReindexerPersistentProperty sourceProperty, ReindexerPersistentProperty targetProperty) {
			Object source;
			if (StringUtils.hasText(namespaceReference.lookup())) {
				source = namespaceReference.lookup();
			}
			else {
				source = this.accessor
					.getProperty(this.entity.getRequiredPersistentProperty(namespaceReference.indexName()));
				if (ObjectUtils.isEmpty(source)) {
					return null;
				}
			}
			ReindexerPersistentEntity<?> referenceEntity = MappingReindexerConverter.this.mappingContext
				.getRequiredPersistentEntity(sourceProperty);
			String namespaceName = StringUtils.hasText(namespaceReference.namespace()) ? namespaceReference.namespace()
					: referenceEntity.getNamespace();
			Supplier<Object> callback = () -> {
				if (StringUtils.hasText(namespaceReference.lookup())) {
					Map<String, Object> variables = new HashMap<>();
					if (namespaceReference.lookup().contains("#sort")) {
						variables.put("sort", SortUtils.getSort(namespaceReference.sort()));
					}
					Object evaluated = this.evaluator.evaluate(namespaceReference.lookup(), variables);
					if (!(evaluated instanceof String preparedQuery)) {
						return evaluated;
					}
					preparedQuery = SortUtils.applySort(preparedQuery, namespaceReference.sort());
					try (ResultIterator<?> iterator = executeQuery(preparedQuery, referenceEntity)) {
						if (targetProperty.isCollectionLike()) {
							List<Object> result = new ArrayList<>();
							iterator.forEachRemaining(result::add);
							return result;
						}
						return iterator.hasNext() ? iterator.next() : null;
					}
				}
				String indexName = referenceEntity.getRequiredIdProperty().getName();
				Namespace<?> namespace = openNamespace(namespaceName, referenceEntity);
				Query<?> query = QueryUtils.withJoins(namespace.query(), referenceEntity.getType(),
						MappingReindexerConverter.this.mappingContext, MappingReindexerConverter.this.reindexer);
				Sort sort = SortUtils.getSort(namespaceReference.sort());
				if (sort.isSorted()) {
					for (Order order : sort) {
						query.sort(order.getProperty(), order.isDescending());
					}
				}
				if (source instanceof Collection<?> values) {
					return query.where(indexName, Condition.SET, values).toList();
				}
				return query.where(indexName, Condition.EQ, source).findOne().orElse(null);
			};
			return MappingReindexerConverter.this.lazyLoadingProxyFactory.createLazyLoadingProxy(
					targetProperty.getType(), sourceProperty, callback,
					new NamespaceReferenceSource(namespaceName, source),
					resolvedReference -> readPropertyValue(sourceProperty, targetProperty, resolvedReference));
		}

		private ResultIterator<?> executeQuery(String query, ReindexerPersistentEntity<?> entity) {
			return MappingReindexerConverter.this.reindexer.execSql(query, entity.getType());
		}

		private Namespace<?> openNamespace(String name, ReindexerPersistentEntity<?> entity) {
			Namespace<?> namespace = MappingReindexerConverter.this.reindexer.openNamespace(name,
					entity.getNamespaceOptions(), entity.getType());
			return new TransactionalNamespace<>(namespace);
		}

		@SuppressWarnings("unchecked")
		private <T> T readPropertyValue(ReindexerPersistentProperty sourceProperty,
				ReindexerPersistentProperty targetProperty, Object value) {
			ReindexerConversionContext conversionContext = new ReindexerConversionContext(
					MappingReindexerConverter.this, sourceProperty, MappingReindexerConverter.this.conversionService,
					MappingReindexerConverter.this.conversions);
			PropertyValueConversions valueConversions = MappingReindexerConverter.this.conversions
				.getPropertyValueConversions();
			if (valueConversions != null && valueConversions.hasValueConverter(targetProperty)) {
				PropertyValueConverter<Object, Object, ValueConversionContext<ReindexerPersistentProperty>> valueConverter = valueConversions
					.getValueConverter(targetProperty);
				// If the domain entity is being read the converters have already been
				// applied during deserialization in reindexer-java connector.
				if (sourceProperty == targetProperty) {
					return (T) value;
				}
				return (T) (value != null ? valueConverter.read(value, conversionContext)
						: valueConverter.readNull(conversionContext));
			}
			return (T) conversionContext.read(value, targetProperty.getTypeInformation());
		}

	}

	private static final class BeanPropertyAccessor implements PropertyAccessor {

		@Override
		public boolean canRead(EvaluationContext context, Object target, String name) {
			return true;
		}

		@Override
		public TypedValue read(EvaluationContext context, Object target, String name) {
			if (target == null) {
				return TypedValue.NULL;
			}
			Object value = BeanPropertyUtils.getProperty(target, name);
			return value != null ? new TypedValue(value) : TypedValue.NULL;
		}

		@Override
		public boolean canWrite(EvaluationContext context, Object target, String name) {
			return false;
		}

		@Override
		public void write(EvaluationContext context, Object target, String name, Object newValue) {
			// NOOP
		}

		@Override
		public Class<?>[] getSpecificTargetClasses() {
			return new Class[] { Object.class };
		}

	}

}
