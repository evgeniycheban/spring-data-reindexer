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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.repository.query.ReturnedType;
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
			if (namespaceReference.lazy() || StringUtils.hasText(namespaceReference.lookup())) {
				continue;
			}
			ReindexerPersistentEntity<?> referencedEntity = mappingContext
				.getRequiredPersistentEntity(persistentProperty.getActualType());
			String namespaceName = StringUtils.hasText(namespaceReference.namespace()) ? namespaceReference.namespace()
					: referencedEntity.getNamespace();
			Namespace<?> namespace = reindexer.openNamespace(namespaceName, referencedEntity.getNamespaceOptions(),
					referencedEntity.getType());
			String indexName = StringUtils.hasText(namespaceReference.referencedIndexName())
					? namespaceReference.referencedIndexName() : referencedEntity.getRequiredIdProperty().getName();
			Query<?> on = namespace.query()
				.on(namespaceReference.indexName(),
						persistentProperty.isCollectionLike() ? Condition.SET : Condition.EQ, indexName);
			if (namespaceReference.joinType() == JoinType.LEFT) {
				criteria.leftJoin(on, persistentProperty.getName());
			}
			else {
				criteria.innerJoin(on, persistentProperty.getName());
			}
		}
		return criteria;
	}

	/**
	 * Returns field names to use in a select clause.
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param returnedType the {@link ReturnedType} to use
	 * @param distinct whether the fields are used in a distinct clause
	 * @return the field names to use
	 */
	public static Collection<String> getSelectFields(ReindexerMappingContext mappingContext, ReturnedType returnedType,
			boolean distinct) {
		ReindexerPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(returnedType.getDomainType());
		Set<String> result = new LinkedHashSet<>(returnedType.getInputProperties());
		for (ReindexerPersistentProperty referenceProperty : entity.getPersistentProperties(NamespaceReference.class)) {
			if (!result.remove(referenceProperty.getName())) {
				continue;
			}
			NamespaceReference namespaceReference = referenceProperty.getNamespaceReference();
			if (StringUtils.hasText(namespaceReference.lookup())) {
				/*
				 * The indexName is added to the input properties passively if a lookup
				 * query contains SpEL expression and indexName, therefore, indexName is
				 * considered being used within the expression.
				 */
				if (namespaceReference.lookup().contains("#{") && StringUtils.hasText(namespaceReference.indexName())
						&& namespaceReference.lookup().contains(namespaceReference.indexName())) {
					result.add(namespaceReference.indexName());
				}
				continue;
			}
			if (namespaceReference.lazy() || distinct) {
				result.add(namespaceReference.indexName());
			}
		}
		return result;
	}

}
