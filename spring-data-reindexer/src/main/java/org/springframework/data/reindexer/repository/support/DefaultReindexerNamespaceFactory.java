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
package org.springframework.data.reindexer.repository.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import ru.rt.restream.reindexer.CollateMode;
import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.IndexType;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.exceptions.IndexConflictException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;

/**
 * A {@link ReindexerNamespaceFactory} that manages {@link Namespace} instances.
 *
 * @author Evgeniy Cheban
 * @since 1.7
 */
public final class DefaultReindexerNamespaceFactory implements ReindexerNamespaceFactory {

	private static final Log LOGGER = LogFactory.getLog(DefaultReindexerNamespaceFactory.class);

	// Copy of ReindexAnnotationScanner#MAPPED_TYPES
	// @formatter:off
	private static final Map<Class<?>, FieldType> MAPPED_TYPES = Map.ofEntries(
			Map.entry(boolean.class, FieldType.BOOL),
			Map.entry(Boolean.class, FieldType.BOOL),
			Map.entry(byte.class, FieldType.INT),
			Map.entry(Byte.class, FieldType.INT),
			Map.entry(short.class, FieldType.INT),
			Map.entry(Short.class, FieldType.INT),
			Map.entry(int.class, FieldType.INT),
			Map.entry(Integer.class, FieldType.INT),
			Map.entry(long.class, FieldType.INT64),
			Map.entry(Long.class, FieldType.INT64),
			Map.entry(float.class, FieldType.FLOAT),
			Map.entry(Float.class, FieldType.FLOAT),
			Map.entry(double.class, FieldType.DOUBLE),
			Map.entry(Double.class, FieldType.DOUBLE),
			Map.entry(String.class, FieldType.STRING),
			Map.entry(char.class, FieldType.STRING),
			Map.entry(Character.class, FieldType.STRING),
			Map.entry(UUID.class, FieldType.UUID)
	);
	// @formatter:on

	private final ConcurrentLruCache<Class<?>, Namespace<?>> cache = new ConcurrentLruCache<>(32,
			this::doOpenNamespace);

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	/**
	 * Creates an instance.
	 * @param reindexer the {@link Reindexer} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 */
	public DefaultReindexerNamespaceFactory(Reindexer reindexer, ReindexerMappingContext mappingContext) {
		Assert.notNull(reindexer, "reindexer cannot be null");
		Assert.notNull(mappingContext, "mappingContext cannot be null");
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
	}

	/**
	 * Opens a {@link Namespace} or retrieves an existing one from the {@code cache}.
	 * <p>
	 * A {@link Namespace} is wrapped in {@link TransactionalNamespace} that is aware of
	 * the currently active transaction;
	 * <p>
	 * Creates missing indexes in Reindexer with default configuration.
	 * @param <T> the domain type to use
	 * @param domainType the domain class to use
	 * @return the {@link Namespace} to use
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> Namespace<T> openNamespace(Class<T> domainType) {
		return (Namespace<T>) this.cache.get(domainType);
	}

	private <T> TransactionalNamespace<T> doOpenNamespace(Class<T> type) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(type);
		ReindexerNamespace<T> namespace = (ReindexerNamespace<T>) this.reindexer.openNamespace(entity.getNamespace(),
				entity.getNamespaceOptions(), type);
		createMissingIndexesIfNeeded(namespace, entity);
		return new TransactionalNamespace<>(namespace);
	}

	private void createMissingIndexesIfNeeded(ReindexerNamespace<?> namespace, ReindexerPersistentEntity<?> entity) {
		if (!this.mappingContext.isAutoIndexCreation()) {
			LOGGER.trace("Auto index creation is disabled; skipping");
			return;
		}
		// Create missing indexes in Reindexer with the default configuration.
		Map<String, ReindexerIndex> indexes = namespace.getIndexes()
			.stream()
			.collect(Collectors.toMap(ReindexerIndex::getName, Function.identity()));
		for (ReindexerPersistentProperty property : entity) {
			if (property.isIdProperty() && !indexes.containsKey(property.getName())) {
				createDefaultPkIndex(namespace, property);
			}
		}
	}

	private void createDefaultPkIndex(ReindexerNamespace<?> namespace, ReindexerPersistentProperty property) {
		FieldType fieldType = MAPPED_TYPES.get(property.getType());
		Assert.notNull(fieldType, () -> "Unmapped type: %s for property: %s.%s".formatted(property.getType(),
				property.getOwner().getName(), property.getName()));
		boolean validPkFieldType = switch (fieldType) {
			case INT, INT64, STRING, UUID -> true;
			default -> false;
		};
		Assert.isTrue(validPkFieldType, () -> "Unsupported primary key fieldType: %s for property: %s.%s"
			.formatted(fieldType, property.getOwner().getName(), property.getName()));
		ReindexerIndex index = ReindexerIndex.builder()
			.fieldType(fieldType)
			.name(property.getName())
			.jsonPaths(List.of(property.getName()))
			.isPk(true)
			.collateMode(CollateMode.NONE)
			.indexType(IndexType.DEFAULT)
			.sortOrder("")
			.build();
		createIndex(namespace.getName(), index);
	}

	private void createIndex(String namespaceName, ReindexerIndex index) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Creating index: %s in namespace: %s".formatted(index.getName(), namespaceName));
		}
		try {
			this.reindexer.addIndex(namespaceName, index);
		}
		catch (IndexConflictException e) {
			if (LOGGER.isWarnEnabled()) {
				LOGGER.warn(
						"Index: %s already exists in namespace: %s; skipping".formatted(index.getName(), namespaceName),
						e);
			}
		}
	}

}
