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

import ru.rt.restream.reindexer.NamespaceOptions;

import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.repository.core.EntityInformation;

/**
 * Reindexer-specific {@link EntityInformation}.
 *
 * @author Evgeniy Cheban
 */
public interface ReindexerEntityInformation<T, ID> extends EntityInformation<T, ID> {

	/**
	 * Returns the name of the namespace the entity shall be persisted to.
	 *
	 * @return the name of the namespace the entity shall be persisted to
	 */
	String getNamespaceName();

	/**
	 * Returns a {@link NamespaceOptions}.
	 *
	 * @return the {@link NamespaceOptions} to use
	 */
	NamespaceOptions getNamespaceOptions();

	/**
	 * Returns the field that the id will be persisted to.
	 *
	 * @return the field that the id will be persisted to
	 */
	String getIdFieldName();

	/**
	 * Returns an iterable of {@link NamespaceReference}.
	 *
	 * @return the iterable of {@link NamespaceReference} to use
	 */
	Iterable<ReindexerPersistentProperty> getNamespaceReferences();

}
