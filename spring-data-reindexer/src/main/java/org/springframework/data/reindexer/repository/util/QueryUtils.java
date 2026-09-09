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
package org.springframework.data.reindexer.repository.util;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.binding.Consts;

import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.reindexer.repository.support.ReindexerNamespaceFactory;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.util.StringUtils;

/**
 * Provides utility methods for customizing a {@link Query}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public final class QueryUtils {

	private static final Log logger = LogFactory.getLog(QueryUtils.class);

	private QueryUtils() {
		throw new IllegalStateException("Cannot instantiate a utility class!");
	}

	/**
	 * Adds {@link NamespaceReference} join declarations to the provided {@link Query}.
	 * <p>
	 * Note: since 1.7 version, Reindexer supports nested joins, depending on a query
	 * format version supported by the server, nested references are fetched using native
	 * nested joins support or using {@link NamespaceReference#fetch()} flag with proxies.
	 * <p>
	 * The self-join references are fetched differently depending on the query format
	 * version:
	 * <ul>
	 * <li>For {@link Consts#QUERY_FORMAT_V1 V1} the first level self-joins are fetched
	 * using native join support, deeply nested self-joins are fetched lazily using
	 * proxies.</li>
	 * <li>For {@link Consts#QUERY_FORMAT_V2 V2} the self-joins are fetched lazily using
	 * proxies.</li>
	 * </ul>
	 * @param criteria the {@link Query} to use
	 * @param domainType the entity domain class to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param namespaceFactory the {@link ReindexerNamespaceFactory} to use
	 * @return the {@link Query} for further customizations
	 */
	public static Query<?> withJoins(Query<?> criteria, Class<?> domainType, ReindexerMappingContext mappingContext,
			ReindexerNamespaceFactory namespaceFactory) {
		return withJoins(criteria, domainType, mappingContext, namespaceFactory, EnumSet.allOf(JoinType.class));
	}

	/**
	 * Adds {@link NamespaceReference} only {@link JoinType#INNER inner join} declarations
	 * to the provided {@link Query}. Useful for update/delete queries.
	 * <p>
	 * Note: since 1.7 version, Reindexer supports nested joins, depending on a query
	 * format version supported by the server, nested references are fetched using native
	 * nested joins support or using {@link NamespaceReference#fetch()} flag with proxies.
	 * <p>
	 * The self-join references are fetched differently depending on the query format
	 * version:
	 * <ul>
	 * <li>For {@link Consts#QUERY_FORMAT_V1 V1} the first level self-joins are fetched
	 * using native join support, deeply nested self-joins are fetched lazily using
	 * proxies.</li>
	 * <li>For {@link Consts#QUERY_FORMAT_V2 V2} the self-joins are fetched lazily using
	 * proxies.</li>
	 * </ul>
	 * @param criteria the {@link Query} to use
	 * @param domainType the entity domain class to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param namespaceFactory the {@link ReindexerNamespaceFactory} to use
	 * @return the {@link Query} for further customizations
	 * @since 1.7
	 */
	public static Query<?> withInnerJoins(Query<?> criteria, Class<?> domainType,
			ReindexerMappingContext mappingContext, ReindexerNamespaceFactory namespaceFactory) {
		return withJoins(criteria, domainType, mappingContext, namespaceFactory, EnumSet.of(JoinType.INNER));
	}

	private static Query<?> withJoins(Query<?> criteria, Class<?> domainType, ReindexerMappingContext mappingContext,
			ReindexerNamespaceFactory namespaceFactory, Set<JoinType> joinTypes) {
		ReindexerPersistentEntity<?> persistentEntity = mappingContext.getRequiredPersistentEntity(domainType);
		for (ReindexerPersistentProperty persistentProperty : persistentEntity
			.getPersistentProperties(NamespaceReference.class)) {
			NamespaceReference namespaceReference = persistentProperty.getNamespaceReference();
			if (!joinTypes.contains(namespaceReference.joinType()) || namespaceReference.lazy()
					|| StringUtils.hasText(namespaceReference.lookup())) {
				continue;
			}
			ReindexerPersistentEntity<?> referencedEntity = mappingContext
				.getRequiredPersistentEntity(persistentProperty);
			boolean isSelfJoinV2 = mappingContext.getQueryFormatVersion() == Consts.QUERY_FORMAT_V2
					&& referencedEntity.getType().isAssignableFrom(domainType);
			if (isSelfJoinV2) {
				if (logger.isTraceEnabled()) {
					logger.trace(
							"Circular reference detected: %s.%s; The self-join (V2) property will be fetched lazily using proxy"
								.formatted(persistentEntity.getName(), persistentProperty.getName()));
				}
				continue;
			}
			Namespace<?> namespace = namespaceFactory.openNamespace(referencedEntity.getType());
			ReindexerPersistentProperty referencedProperty = StringUtils
				.hasText(namespaceReference.referencedIndexName())
						? referencedEntity.getRequiredPersistentProperty(namespaceReference.referencedIndexName())
						: referencedEntity.getRequiredIdProperty();
			ReindexerPersistentProperty joinProperty = persistentEntity
				.getRequiredPersistentProperty(namespaceReference.indexName());
			Query<?> joinQuery = mappingContext.getQueryFormatVersion() == Consts.QUERY_FORMAT_V2
					? withJoins(namespace.query(), referencedEntity.getType(), mappingContext, namespaceFactory,
							joinTypes)
					: namespace.query();
			joinQuery.on(joinProperty.getIndexName(), joinProperty.isCollectionLike() ? Condition.SET : Condition.EQ,
					referencedProperty.getIndexName());
			if (namespaceReference.joinType() == JoinType.LEFT) {
				criteria.leftJoin(joinQuery, persistentProperty.getName());
			}
			else {
				criteria.innerJoin(joinQuery, persistentProperty.getName());
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
