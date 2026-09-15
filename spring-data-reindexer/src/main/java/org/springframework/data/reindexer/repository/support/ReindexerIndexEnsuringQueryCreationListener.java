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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import ru.rt.restream.reindexer.CollateMode;
import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.IndexType;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.exceptions.IndexConflictException;

import org.springframework.data.reindexer.core.convert.ReindexerSimpleTypes;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.data.reindexer.repository.query.PartTreeReindexerQuery;
import org.springframework.data.repository.core.support.QueryCreationListener;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * A {@link QueryCreationListener} that inspects a {@link PartTreeReindexerQuery} and
 * creates indexes for properties it refers to.
 * <p>
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 * @see EnableReindexerRepositories#createIndexesForQueryMethods()
 */
final class ReindexerIndexEnsuringQueryCreationListener implements QueryCreationListener<PartTreeReindexerQuery> {

	private static final Log logger = LogFactory.getLog(ReindexerIndexEnsuringQueryCreationListener.class);

	private final ReindexerMappingContext mappingContext;

	private final Reindexer reindexer;

	ReindexerIndexEnsuringQueryCreationListener(ReindexerMappingContext mappingContext, Reindexer reindexer) {
		this.mappingContext = mappingContext;
		this.reindexer = reindexer;
	}

	@Override
	public void onCreation(PartTreeReindexerQuery query) {
		PartTree tree = query.getTree();
		if (!tree.hasPredicate()) {
			if (logger.isTraceEnabled()) {
				logger.trace("%s has no predicate; skipping".formatted(tree));
			}
			return;
		}
		ReindexerQueryMethod method = query.getQueryMethod();
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(method.getDomainClass());
		for (Part part : tree.getParts()) {
			ReindexerPersistentProperty property = this.mappingContext.getPersistentPropertyPath(part.getProperty())
				.getLeafProperty();
			if (property.isIndexedProperty() || property.isIdProperty()) {
				continue;
			}
			Class<?> actualType = property.getActualType();
			FieldType fieldType = ReindexerSimpleTypes.MAPPED_TYPES.get(actualType);
			if (fieldType == null) {
				if (logger.isWarnEnabled()) {
					logger.warn("Could not determine the Reindexer type for: %s; consider adding @Reindex to: %s"
						.formatted(actualType.getName(), part.getProperty()));
				}
				continue;
			}
			String path = part.getProperty().toDotPath();
			String collation = method.hasAnnotatedCollation() ? method.getAnnotatedCollation() : "";
			CollateMode collateMode = getCollateMode(collation);
			String sortOrder = getSortOrder(collateMode, collation);
			ReindexerIndex index = ReindexerIndex.builder()
				.fieldType(fieldType)
				.name(path)
				.jsonPaths(List.of(path))
				.isArray(property.isCollectionLike())
				.indexType(IndexType.DEFAULT)
				.collateMode(collateMode)
				.sortOrder(sortOrder)
				.build();
			createIndex(entity.getNamespace(), index);
		}
	}

	private CollateMode getCollateMode(String collation) {
		for (CollateMode collateMode : CollateMode.values()) {
			if (collateMode.getName().equals(collation)) {
				return collateMode;
			}
		}
		return CollateMode.CUSTOM;
	}

	private String getSortOrder(CollateMode collateMode, String collation) {
		if (CollateMode.CUSTOM == collateMode) {
			return collation;
		}
		return "";
	}

	private void createIndex(String namespaceName, ReindexerIndex index) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating index: %s in namespace: %s".formatted(index.getName(), namespaceName));
		}
		try {
			this.reindexer.addIndex(namespaceName, index);
		}
		catch (IndexConflictException ex) {
			if (logger.isWarnEnabled()) {
				logger.warn(
						"Index: %s already exists in namespace: %s; skipping".formatted(index.getName(), namespaceName),
						ex);
			}
		}
	}

}
