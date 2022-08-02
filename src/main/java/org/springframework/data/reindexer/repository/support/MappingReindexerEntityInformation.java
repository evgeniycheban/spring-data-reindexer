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

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ru.rt.restream.reindexer.NamespaceOptions;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.util.Assert;

/**
 * {@link ReindexerEntityInformation} implementation using a domain class to lookup the necessary
 * information.
 *
 * @author Evgeniy Cheban
 */
public class MappingReindexerEntityInformation<T, ID> implements ReindexerEntityInformation<T, ID> {

	private static final Map<Class<?>, MappingReindexerEntityInformation<?, ?>> CACHE = new ConcurrentHashMap<>();

	private final Class<T> domainClass;

	private final Field idField;

	private final String namespaceName;

	private final NamespaceOptions namespaceOptions;

	/**
	 * Creates and caches an instance of {@link MappingReindexerEntityInformation} for the given domain class.
	 *
	 * @param domainClass the domain class to use
	 * @return the {@link MappingReindexerEntityInformation} to use
	 * @since 1.1
	 */
	@SuppressWarnings("unchecked")
	public static <T, ID> MappingReindexerEntityInformation<T, ID> getInstance(Class<T> domainClass) {
		return (MappingReindexerEntityInformation<T, ID>) CACHE.computeIfAbsent(domainClass, MappingReindexerEntityInformation::new);
	}

	/**
	 * Creates an instance.
	 *
	 * @param domainClass the domain class to use
	 */
	public MappingReindexerEntityInformation(Class<T> domainClass) {
		this.domainClass = domainClass;
		this.idField = getIdField(domainClass);
		Namespace namespaceAnnotation = domainClass.getAnnotation(Namespace.class);
		Assert.state(namespaceAnnotation != null, () -> "@Namespace annotation is not found on " + domainClass);
		this.namespaceName = namespaceAnnotation.name();
		this.namespaceOptions = new NamespaceOptions(namespaceAnnotation.enableStorage(),
				namespaceAnnotation.createStorageIfMissing(), namespaceAnnotation.dropOnIndexesConflict(),
				namespaceAnnotation.dropOnFileFormatError(), namespaceAnnotation.disableObjCache(),
				namespaceAnnotation.objCacheItemsCount());
	}

	private Field getIdField(Class<T> domainClass) {
		Field idField = ReflectionUtils.findField(domainClass, new ReflectionUtils.DescribedFieldFilter() {
			@Override
			public String getDescription() {
				return "Found more than one field with @Reindex(isPrimaryKey = true) in " + domainClass;
			}

			@Override
			public boolean matches(Field field) {
				Reindex reindexAnnotation = field.getAnnotation(Reindex.class);
				return reindexAnnotation != null && reindexAnnotation.isPrimaryKey();
			}
		}, true);
		Assert.state(idField != null, () -> "ID is not found consider adding @Reindex(isPrimaryKey = true) field to " + domainClass);
		org.springframework.util.ReflectionUtils.makeAccessible(idField);
		return idField;
	}

	@Override
	public String getNamespaceName() {
		return this.namespaceName;
	}

	@Override
	public NamespaceOptions getNamespaceOptions() {
		return this.namespaceOptions;
	}

	@Override
	public String getIdFieldName() {
		return this.idField.getName();
	}

	@Override
	public boolean isNew(T entity) {
		return getIdValue(entity) == null;
	}

	@Override
	public ID getId(T entity) {
		return getIdValue(entity);
	}

	@SuppressWarnings("unchecked")
	private ID getIdValue(T entity) {
		return (ID) org.springframework.util.ReflectionUtils.getField(this.idField, entity);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<ID> getIdType() {
		return (Class<ID>) this.idField.getType();
	}

	@Override
	public Class<T> getJavaType() {
		return this.domainClass;
	}

}
