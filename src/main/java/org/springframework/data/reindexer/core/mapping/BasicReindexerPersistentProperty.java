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
package org.springframework.data.reindexer.core.mapping;

import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.annotations.Transient;

import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.Lazy;

/**
 * Reindexer specific {@link org.springframework.data.mapping.PersistentProperty} implementation.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class BasicReindexerPersistentProperty extends AnnotationBasedPersistentProperty<ReindexerPersistentProperty>
		implements ReindexerPersistentProperty {

	private final Lazy<NamespaceReference> getReference = Lazy.of(() -> findAnnotation(NamespaceReference.class));

	private final Lazy<Boolean> isIdProperty = Lazy.of(() -> {
		if (super.isIdProperty()) {
			return true;
		}
		Reindex reindex = findAnnotation(Reindex.class);
		return reindex != null && reindex.isPrimaryKey();
	});

	private final Lazy<Boolean> isTransient = Lazy.of(() -> !isNamespaceReference()
			&& (super.isTransient() || isAnnotationPresent(Transient.class)));

	/**
	 * Creates a new {@link BasicReindexerPersistentProperty}.
	 *
	 * @param property must not be {@literal null}
	 * @param owner must not be {@literal null}
	 * @param simpleTypeHolder must not be {@literal null}
	 */
	public BasicReindexerPersistentProperty(Property property, PersistentEntity<?, ReindexerPersistentProperty> owner, SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);
	}

	@Override
	protected Association<ReindexerPersistentProperty> createAssociation() {
		return new Association<>(this, null);
	}

	@Override
	public boolean isNamespaceReference() {
		return this.getReference.getNullable() != null;
	}

	@Override
	public NamespaceReference getNamespaceReference() {
		return this.getReference.get();
	}

	@Override
	public boolean isIdProperty() {
		return this.isIdProperty.get();
	}

	@Override
	public boolean isTransient() {
		return this.isTransient.get();
	}

}
