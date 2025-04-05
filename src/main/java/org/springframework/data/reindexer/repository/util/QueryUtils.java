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
package org.springframework.data.reindexer.repository.util;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.util.StringUtils;

/**
 * Provides utility methods for customizing a {@link Query}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public final class QueryUtils {

	private QueryUtils() {
		throw new IllegalStateException("Cannot instantiate a utility class!");
	}

	/**
	 * Adds {@link NamespaceReference} join declarations to the provided {@link Query}.
	 * @param criteria the {@link Query} to use
	 * @param domainType the entity domain class to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @return the {@link Query} for further customizations
	 */
	public static Query<?> withJoins(Query<?> criteria, Class<?> domainType, ReindexerMappingContext mappingContext,
			Reindexer reindexer) {
		ReindexerPersistentEntity<?> persistentEntity = mappingContext.getRequiredPersistentEntity(domainType);
		for (ReindexerPersistentProperty persistentProperty : persistentEntity
			.getPersistentProperties(NamespaceReference.class)) {
			NamespaceReference namespaceReference = persistentProperty.getNamespaceReference();
			if (namespaceReference.lazy()) {
				continue;
			}
			ReindexerPersistentEntity<?> referencedEntity = mappingContext
				.getRequiredPersistentEntity(persistentProperty.getActualType());
			String namespaceName = StringUtils.hasText(namespaceReference.namespace()) ? namespaceReference.namespace()
					: referencedEntity.getNamespace();
			Namespace<?> namespace = reindexer.openNamespace(namespaceName, referencedEntity.getNamespaceOptions(),
					referencedEntity.getType());
			Query<?> on = namespace.query()
				.on(namespaceReference.indexName(),
						persistentProperty.isCollectionLike() ? Condition.SET : Condition.EQ,
						referencedEntity.getRequiredIdProperty().getName());
			if (namespaceReference.joinType() == JoinType.LEFT) {
				criteria.leftJoin(on, persistentProperty.getName());
			}
			else {
				criteria.innerJoin(on, persistentProperty.getName());
			}
		}
		return criteria;
	}

}
