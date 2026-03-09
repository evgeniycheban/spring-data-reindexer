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
package org.springframework.data.reindexer.core.mapping;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;

import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.util.Assert;

/**
 * Default implementation of a {@link MappingContext} for Reindexer using
 * {@link BasicReindexerPersistentEntity} and {@link BasicReindexerPersistentProperty} as
 * primary abstractions.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class ReindexerMappingContext
		extends AbstractMappingContext<ReindexerPersistentEntity<?>, ReindexerPersistentProperty> {

	private final Map<String, ReindexerPersistentEntity<?>> namespaceEntityMap = new ConcurrentHashMap<>();

	/**
	 * Returns a {@link ReindexerPersistentEntity} for the given {@code namespaceName}.
	 * @param namespaceName the namespace name to use, must not be empty
	 * @return the {@link ReindexerPersistentEntity} to use
	 * @throws MappingException if there is no {@code ReindexerPersistentEntity}
	 * associated with the given {@code namespaceName}
	 * @since 1.6
	 */
	public final ReindexerPersistentEntity<?> getRequiredPersistentEntity(String namespaceName) {
		Assert.hasText(namespaceName, "namespaceName must not be empty");
		ReindexerPersistentEntity<?> entity = this.namespaceEntityMap.get(namespaceName);
		if (entity == null) {
			throw new MappingException("Unknown persistent entity: " + namespaceName);
		}
		return entity;
	}

	@Override
	protected @NonNull Optional<ReindexerPersistentEntity<?>> addPersistentEntity(
			@NonNull TypeInformation<?> typeInformation) {
		Optional<ReindexerPersistentEntity<?>> entity = super.addPersistentEntity(typeInformation);
		entity.ifPresent((e) -> this.namespaceEntityMap.putIfAbsent(e.getNamespace(), e));
		return entity;
	}

	@Override
	protected <T> ReindexerPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		return new BasicReindexerPersistentEntity<>(typeInformation);
	}

	@Override
	protected ReindexerPersistentProperty createPersistentProperty(Property property,
			ReindexerPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
		return new BasicReindexerPersistentProperty(property, owner, simpleTypeHolder);
	}

}
