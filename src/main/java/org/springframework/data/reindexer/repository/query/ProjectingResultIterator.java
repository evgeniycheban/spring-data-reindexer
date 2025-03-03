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
package org.springframework.data.reindexer.repository.query;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import ru.rt.restream.reindexer.AggregationResult;
import ru.rt.restream.reindexer.AggregationResult.Facet;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.NamespaceOptions;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.model.PreferredConstructorDiscoverer;
import org.springframework.data.reindexer.core.convert.LazyLoadingProxyFactory;
import org.springframework.data.reindexer.core.convert.NamespaceReferenceSource;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ProjectingResultIterator implements ResultIterator<Object> {

	private static final Map<Class<?>, Constructor<?>> cache = new ConcurrentHashMap<>();

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final ResultIterator<?> delegate;

	private final ReturnedType projectionType;

	private final AggregationResult aggregationFacet;

	private final Map<String, Set<String>> distinctAggregationResults;

	private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

	private final LazyLoadingProxyFactory lazyLoadingProxyFactory = new LazyLoadingProxyFactory();

	private int aggregationPosition;

	ProjectingResultIterator(Reindexer reindexer, ReindexerMappingContext mappingContext, Query<?> query, ReturnedType projectionType) {
		this(reindexer, mappingContext, query.execute(), projectionType);
	}

	ProjectingResultIterator(Reindexer reindexer, ReindexerMappingContext mappingContext, ResultIterator<?> delegate, ReturnedType projectionType) {
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.delegate = delegate;
		this.projectionType = projectionType;
		this.aggregationFacet = getAggregationFacet();
		this.distinctAggregationResults = getDistinctAggregationResults();
	}

	@Override
	public long getTotalCount() {
		return this.delegate.getTotalCount();
	}

	@Override
	public long size() {
		return this.delegate.size();
	}

	@Override
	public List<AggregationResult> aggResults() {
		return this.delegate.aggResults();
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	@Override
	public boolean hasNext() {
		return this.delegate.hasNext() || this.aggregationFacet != null && this.aggregationPosition < this.aggregationFacet.getFacets().size();
	}

	@Override
	public Object next() {
		if (this.aggregationFacet != null && !this.distinctAggregationResults.isEmpty()) {
			Object item = null;
			Object[] arguments = null;
			List<String> fields = this.aggregationFacet.getFields();
			if (this.projectionType.needsCustomConstruction() && !this.projectionType.getReturnedType().isInterface()) {
				arguments = new Object[fields.size()];
			}
			else {
				try {
					item = this.projectionType.getDomainType().getDeclaredConstructor().newInstance();
				}
				catch (NoSuchMethodException | InvocationTargetException |
					   InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
			for (int i = 0; i < fields.size(); i++) {
				String field = fields.get(i);
				Facet facet = this.aggregationFacet.getFacets().get(this.aggregationPosition);
				if (i < facet.getValues().size() && this.distinctAggregationResults.get(field).remove(facet.getValues().get(i))) {
					Object value = this.conversionService.convert(facet.getValues().get(i), ReflectionUtils.findRequiredField(this.projectionType.getDomainType(), field).getType());
					if (arguments != null) {
						arguments[i] = value;
					}
					else {
						BeanPropertyUtils.setProperty(item, field, value);
					}
				}
				else {
					this.aggregationPosition++;
					return null;
				}
			}
			this.aggregationPosition++;
			if (item != null) {
				return item;
			}
			return constructTargetObject(arguments);
		}
		Object item = this.delegate.next();
		ReindexerPersistentEntity<?> persistentEntity = this.mappingContext.getRequiredPersistentEntity(this.projectionType.getDomainType());
		if (this.projectionType.needsCustomConstruction() && !this.projectionType.getReturnedType().isInterface()) {
			List<String> properties = this.projectionType.getInputProperties();
			Object[] values = new Object[properties.size()];
			for (int i = 0; i < properties.size(); i++) {
				String property = properties.get(i);
				ReindexerPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty(property);
				Object proxy = createProxyIfNeeded(persistentProperty, item);
				values[i] = proxy != null ? proxy : BeanPropertyUtils.getProperty(item, property);
			}
			return constructTargetObject(values);
		}
		for (ReindexerPersistentProperty property : persistentEntity.getPersistentProperties(NamespaceReference.class)) {
			Object proxy = createProxyIfNeeded(property, item);
			if (proxy != null) {
				BeanPropertyUtils.setProperty(item, property.getName(), proxy);
			}
		}
		return item;
	}

	private Object createProxyIfNeeded(ReindexerPersistentProperty property, Object entity) {
		if (property.isNamespaceReference()) {
			NamespaceReference namespaceReference = property.getNamespaceReference();
			if (namespaceReference.lazy()) {
				ReindexerPersistentEntity<?> referenceEntity = this.mappingContext.getRequiredPersistentEntity(property);
				String namespaceName = StringUtils.hasText(namespaceReference.namespace()) ? namespaceReference.namespace()
						: referenceEntity.getNamespace();
				Object source = BeanPropertyUtils.getProperty(entity, namespaceReference.indexName());
				if (source != null) {
					Supplier<Object> callback = () -> {
						Namespace<?> namespace = this.reindexer.openNamespace(namespaceName, NamespaceOptions.defaultOptions(), referenceEntity.getType());
						if (property.isCollectionLike()) {
							return namespace.query().where(referenceEntity.getRequiredIdProperty().getName(), Condition.SET, (Collection<?>) source).toList();
						}
						return namespace.query().where(referenceEntity.getRequiredIdProperty().getName(), Condition.EQ, source).getOne();
					};
					return this.lazyLoadingProxyFactory.createLazyLoadingProxy(property, callback, new NamespaceReferenceSource(namespaceName, source));
				}
			}
		}
		return null;
	}

	private Object constructTargetObject(Object[] values) {
		Constructor<?> constructor = cache.computeIfAbsent(this.projectionType.getReturnedType(), (type) -> {
			PreferredConstructor<?, ?> preferredConstructor = PreferredConstructorDiscoverer.discover(type);
			Assert.state(preferredConstructor != null, () -> "No preferred constructor found for " + type);
			return preferredConstructor.getConstructor();
		});
		try {
			return constructor.newInstance(values);
		}
		catch (InvocationTargetException | InstantiationException |
			   IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private Map<String, Set<String>> getDistinctAggregationResults() {
		Map<String, Set<String>> result = new HashMap<>();
		for (AggregationResult aggregationResult : aggResults()) {
			if ("distinct".equals(aggregationResult.getType())) {
				result.put(aggregationResult.getFields().get(0), new HashSet<>(aggregationResult.getDistincts()));
			}
		}
		return result;
	}

	private AggregationResult getAggregationFacet() {
		for (AggregationResult aggregationResult : aggResults()) {
			if ("facet".equals(aggregationResult.getType())) {
				return aggregationResult;
			}
		}
		return null;
	}
}
