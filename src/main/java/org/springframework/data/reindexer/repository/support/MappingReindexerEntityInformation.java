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

import ru.rt.restream.reindexer.NamespaceOptions;

import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;

/**
 * {@link ReindexerEntityInformation} implementation using a domain class to lookup the
 * necessary information.
 *
 * @author Evgeniy Cheban
 */
public class MappingReindexerEntityInformation<T, ID> implements ReindexerEntityInformation<T, ID> {

	private final ReindexerPersistentEntity<T> metadata;

	/**
	 * Creates an instance.
	 * @param metadata the {@link ReindexerPersistentEntity} to use
	 */
	public MappingReindexerEntityInformation(ReindexerPersistentEntity<T> metadata) {
		this.metadata = metadata;
	}

	@Override
	public String getNamespaceName() {
		return this.metadata.getNamespace();
	}

	@Override
	public NamespaceOptions getNamespaceOptions() {
		return this.metadata.getNamespaceOptions();
	}

	@Override
	public String getIdFieldName() {
		return this.metadata.getRequiredIdProperty().getName();
	}

	@Override
	public boolean isNew(T entity) {
		return this.metadata.isNew(entity);
	}

	@SuppressWarnings("unchecked")
	@Override
	public ID getId(T entity) {
		return (ID) this.metadata.getIdentifierAccessor(entity).getIdentifier();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<ID> getIdType() {
		return (Class<ID>) this.metadata.getRequiredIdProperty().getType();
	}

	@Override
	public Class<T> getJavaType() {
		return this.metadata.getType();
	}

	@Override
	public Iterable<ReindexerPersistentProperty> getNamespaceReferences() {
		return this.metadata.getPersistentProperties(NamespaceReference.class);
	}

}
