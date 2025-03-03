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

import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;

/**
 * Default implementation of a {@link MappingContext} for Reindexer using {@link BasicReindexerPersistentEntity} and
 * {@link BasicReindexerPersistentProperty} as primary abstractions.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class ReindexerMappingContext extends AbstractMappingContext<ReindexerPersistentEntity<?>, ReindexerPersistentProperty> {

	@Override
	protected <T> ReindexerPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		return new BasicReindexerPersistentEntity<>(typeInformation);
	}

	@Override
	protected ReindexerPersistentProperty createPersistentProperty(Property property, ReindexerPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
		return new BasicReindexerPersistentProperty(property, owner, simpleTypeHolder);
	}

}
