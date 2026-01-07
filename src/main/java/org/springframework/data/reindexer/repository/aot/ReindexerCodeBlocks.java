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
package org.springframework.data.reindexer.repository.aot;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import ru.rt.restream.reindexer.Query;

import org.jspecify.annotations.NullUnmarked;

import org.springframework.core.MethodParameter;
import org.springframework.data.core.PropertyPath;
import org.springframework.data.domain.Sort;
import org.springframework.data.reindexer.repository.query.QueryParameterMapper;
import org.springframework.data.reindexer.repository.util.QueryUtils;
import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.reindexer.repository.query.ProjectingResultIterator;
import org.springframework.data.reindexer.repository.query.ReindexerQueryExecutions;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import org.springframework.data.reindexer.repository.util.PageableUtils;
import org.springframework.data.repository.aot.generate.AotQueryMethodGenerationContext;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.javapoet.CodeBlock;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerCodeBlocks {

	static DerivedQueryCodeBlockBuilder derivedQueryCodeBlockBuilder(PartTree tree,
			AotQueryMethodGenerationContext context, ReindexerMappingContext mappingContext) {
		return new DerivedQueryCodeBlockBuilder(tree, context, mappingContext);
	}

	static DerivedExecutionCodeBlockBuilder derivedExecutionCodeBlockBuilder(PartTree tree,
			AotQueryMethodGenerationContext context, ReindexerMappingContext mappingContext,
			ReindexerQueryMethod queryMethod) {
		return new DerivedExecutionCodeBlockBuilder(tree, context, mappingContext, queryMethod);
	}

	@NullUnmarked
	static final class DerivedQueryCodeBlockBuilder {

		private final PartTree tree;

		private final AotQueryMethodGenerationContext context;

		private final ReindexerMappingContext mappingContext;

		DerivedQueryCodeBlockBuilder(PartTree tree, AotQueryMethodGenerationContext context,
				ReindexerMappingContext mappingContext) {
			this.tree = tree;
			this.context = context;
			this.mappingContext = mappingContext;
		}

		CodeBlock build() {
			CodeBlock.Builder builder = CodeBlock.builder();
			ReindexerPersistentEntity<?> entity = this.mappingContext
				.getRequiredPersistentEntity(this.context.getDomainType());
			Iterator<String> allParameterNames = this.context.getBindableParameterNames().iterator();
			if (allParameterNames.hasNext()) {
				builder.addStatement("$1T $2L = getParameterMapper()", QueryParameterMapper.class,
						this.context.localVariable("parameterMapper"));
			}
			builder.add("$1T<$2T> $3L = query($2T.class)", Query.class, this.context.getDomainType(),
					this.context.localVariable("root"));
			if (this.context.getReturnedType().needsCustomConstruction()) {
				builder.add(createSelectCodeBlock());
			}
			else if (this.tree.isExistsProjection()) {
				builder.add(".select($S)", entity.getRequiredIdProperty().getName());
			}
			builder.add(createJoinCodeBlock(entity));
			builder.add(createWhereCodeBlock(allParameterNames));
			builder.add(createSortCodeBlock());
			builder.add(createLimitCodeBlock());
			return builder.build();
		}

		private CodeBlock createSelectCodeBlock() {
			CodeBlock.Builder builder = CodeBlock.builder();
			Collection<String> inputProperties = QueryUtils.getSelectFields(this.mappingContext,
					this.context.getReturnedType(), this.tree.isDistinct());
			if (this.tree.isDistinct()) {
				for (String field : inputProperties) {
					builder.add(".aggregateDistinct($S)", field);
				}
			}
			else {
				builder.add(".select($L)", createParameterArray(inputProperties));
			}
			return builder.build();
		}

		private CodeBlock createJoinCodeBlock(ReindexerPersistentEntity<?> entity) {
			CodeBlock.Builder builder = CodeBlock.builder();
			for (ReindexerPersistentProperty persistentProperty : entity
				.getPersistentProperties(NamespaceReference.class)) {
				NamespaceReference namespaceReference = persistentProperty.getNamespaceReference();
				boolean shouldSkip = namespaceReference.lazy() //
						|| StringUtils.hasText(namespaceReference.lookup()) //
						|| namespaceReference.joinType() == JoinType.LEFT
								&& (this.tree.isExistsProjection() || this.tree.isCountProjection());
				if (shouldSkip) {
					continue;
				}
				ReindexerPersistentEntity<?> referencedEntity = this.mappingContext
					.getRequiredPersistentEntity(persistentProperty.getActualType());
				String indexName = StringUtils.hasText(namespaceReference.referencedIndexName())
						? namespaceReference.referencedIndexName() : referencedEntity.getRequiredIdProperty().getName();
				Query.Condition condition = persistentProperty.isCollectionLike() ? Query.Condition.SET
						: Query.Condition.EQ;
				CodeBlock onCodeBlock = CodeBlock.of("query($1T.class).on($2S, $3T.$4L, $5S)",
						referencedEntity.getType(), namespaceReference.indexName(), Query.Condition.class, condition,
						indexName);
				if (namespaceReference.joinType() == JoinType.LEFT) {
					builder.add(".leftJoin($1L, $2S)", onCodeBlock, persistentProperty.getName());
				}
				else {
					builder.add(".innerJoin($1L, $2S)", onCodeBlock, persistentProperty.getName());
				}
			}
			return builder.build();
		}

		private CodeBlock createWhereCodeBlock(Iterator<String> allParameterNames) {
			CodeBlock.Builder builder = CodeBlock.builder();
			PartTree.OrPart first = null;
			for (PartTree.OrPart node : this.tree) {
				if (first == null) {
					first = node;
				}
				Iterator<Part> parts = node.iterator();
				if (!parts.hasNext()) {
					throw new IllegalStateException(String.format("No part found in PartTree %s", this.tree));
				}
				boolean groupedOr = node != first;
				if (groupedOr) {
					/*
					 * If this is the next PartTree.OrPart iteration, the OR operator is
					 * applied. Note that we need to open bracket to ensure correct
					 * handling of certain OR conditions.
					 *
					 * For example, in `findByNameOrValueNot`, the NOT part must be
					 * wrapped in brackets to produce correct results.
					 */
					builder.add(".or().openBracket()");
				}
				CodeBlock criteria = createWhereCodeBlock(parts.next(), allParameterNames);
				builder.add(criteria);
				while (parts.hasNext()) {
					builder.add(createWhereCodeBlock(parts.next(), allParameterNames));
				}
				if (groupedOr) {
					// Close the bracket opened in this PartTree.OrPart iteration.
					builder.add(".closeBracket()");
				}
			}
			return builder.build();
		}

		private CodeBlock createWhereCodeBlock(Part part, Iterator<String> allParameterNames) {
			String indexName = part.getProperty().toDotPath();
			return switch (part.getType()) {
				case GREATER_THAN, AFTER -> createWhereCodeBlock(indexName, Query.Condition.GT, allParameterNames);
				case GREATER_THAN_EQUAL -> createWhereCodeBlock(indexName, Query.Condition.GE, allParameterNames);
				case LESS_THAN, BEFORE -> createWhereCodeBlock(indexName, Query.Condition.LT, allParameterNames);
				case LESS_THAN_EQUAL -> createWhereCodeBlock(indexName, Query.Condition.LE, allParameterNames);
				case NOT_IN, IN -> {
					CodeBlock.Builder builder = CodeBlock.builder();
					if (part.getType() == Part.Type.NOT_IN) {
						builder.add(".not()");
					}
					builder.add(createWhereCodeBlock(indexName, Query.Condition.SET, allParameterNames));
					yield builder.build();
				}
				case IS_NOT_NULL -> CodeBlock.of(".isNotNull", Query.class, indexName);
				case IS_NULL -> CodeBlock.of(".isNull", Query.class, indexName);
				case NEGATING_SIMPLE_PROPERTY, SIMPLE_PROPERTY -> {
					boolean isSimpleComparison = switch (part.shouldIgnoreCase()) {
						case NEVER -> true;
						case WHEN_POSSIBLE -> part.getProperty().getType() != String.class;
						case ALWAYS -> false;
					};
					if (isSimpleComparison) {
						CodeBlock.Builder builder = CodeBlock.builder();
						if (part.getType() == Part.Type.NEGATING_SIMPLE_PROPERTY) {
							builder.add(".not()");
						}
						builder.add(createWhereCodeBlock(indexName, Query.Condition.EQ, allParameterNames));
						yield builder.build();
					}
					PropertyPath path = part.getProperty().getLeafProperty();
					if (part.shouldIgnoreCase() == Part.IgnoreCaseType.ALWAYS) {
						Assert.isTrue(part.getProperty().getType() == String.class,
								() -> "Property '" + indexName + "' must be of type String but was " + path.getType());
					}
					CodeBlock.Builder builder = CodeBlock.builder();
					if (part.getType() == Part.Type.NEGATING_SIMPLE_PROPERTY) {
						builder.add(".not()");
					}
					builder.add(".like($1S, $2L)", indexName, allParameterNames.next());
					yield builder.build();
				}
				case BETWEEN -> CodeBlock.of(".where($1S, $2T.$3L, parameterMapper.mapParameterValues($1S, $4L, $5L))",
						indexName, Query.Condition.class, Query.Condition.RANGE, allParameterNames.next(),
						allParameterNames.next());
				case TRUE ->
					CodeBlock.of(".where($1S, $2T.$3L, true)", indexName, Query.Condition.class, Query.Condition.EQ);
				case FALSE ->
					CodeBlock.of(".where($1S, $2T.$3L, false)", indexName, Query.Condition.class, Query.Condition.EQ);
				case LIKE, NOT_LIKE, STARTING_WITH, ENDING_WITH, CONTAINING, NOT_CONTAINING -> {
					if (part.getProperty().getLeafProperty().isCollection()) {
						CodeBlock.Builder builder = CodeBlock.builder();
						if (part.getType() == Part.Type.NOT_CONTAINING) {
							builder.add(".not()");
						}
						builder.add(createWhereCodeBlock(indexName, Query.Condition.SET, allParameterNames));
						yield builder.build();
					}
					Assert.isAssignable(String.class, part.getProperty().getLeafType(),
							() -> "Value of '" + part.getType() + "' expression must be String");
					yield createLikeCodeBlock(part, indexName, allParameterNames);
				}
				default -> throw new IllegalArgumentException("Unsupported part type: " + part.getType());
			};

		}

		private CodeBlock createLikeCodeBlock(Part part, String indexName, Iterator<String> allParameterNames) {
			String parameterName = allParameterNames.next();
			String expression = switch (part.getType()) {
				case STARTING_WITH -> parameterName + " + \"%\"";
				case ENDING_WITH -> "\"%\" + " + parameterName;
				case CONTAINING, NOT_CONTAINING -> "\"%\" + " + parameterName + " + \"%\"";
				default -> parameterName;
			};
			CodeBlock.Builder builder = CodeBlock.builder();
			if (part.getType() == Part.Type.NOT_LIKE || part.getType() == Part.Type.NOT_CONTAINING) {
				builder.add(".not()");
			}
			builder.add(".like($1S, $2L)", indexName, expression);
			return builder.build();
		}

		private CodeBlock createWhereCodeBlock(String indexName, Query.Condition condition,
				Iterator<String> allParameterNames) {
			String parameterName = allParameterNames.next();
			MethodParameter methodParameter = this.context.getMethodParameter(parameterName);
			if (methodParameter.getParameterType().isArray()
					|| Collection.class.isAssignableFrom(methodParameter.getParameterType())) {
				return CodeBlock.of(".where($1S, $2T.$3L, ($4T) parameterMapper.mapParameterValue($1S, $5L))",
						indexName, Query.Condition.class, condition, Collection.class, parameterName);
			}
			return CodeBlock.of(".where($1S, $2T.$3L, parameterMapper.mapParameterValue($1S, $4L))", indexName,
					Query.Condition.class, condition, parameterName);
		}

		private CodeBlock createSortCodeBlock() {
			CodeBlock.Builder builder = CodeBlock.builder();
			Sort sort = this.tree.getSort();
			if (sort.isSorted()) {
				for (Sort.Order order : sort) {
					builder.add(".sort($1S, $2L)", order.getProperty(), order.isDescending());
				}
			}
			return builder.build();
		}

		private CodeBlock createLimitCodeBlock() {
			CodeBlock.Builder builder = CodeBlock.builder();
			if (this.tree.isExistsProjection()) {
				builder.add(".limit(1)");
			}
			else {
				Integer maxResults = this.tree.getMaxResults();
				if (maxResults != null) {
					builder.add(".limit($L)", maxResults);
				}
			}
			return builder.build();
		}

	}

	@NullUnmarked
	static final class DerivedExecutionCodeBlockBuilder {

		private final PartTree tree;

		private final AotQueryMethodGenerationContext context;

		private final ReindexerMappingContext mappingContext;

		private final ReindexerQueryMethod queryMethod;

		DerivedExecutionCodeBlockBuilder(PartTree tree, AotQueryMethodGenerationContext context,
				ReindexerMappingContext mappingContext, ReindexerQueryMethod queryMethod) {
			this.tree = tree;
			this.context = context;
			this.mappingContext = mappingContext;
			this.queryMethod = queryMethod;
		}

		CodeBlock build() {
			CodeBlock.Builder builder = CodeBlock.builder();
			String root = this.context.localVariable("root");
			if (this.tree.isExistsProjection()) {
				return builder.addStatement("return $L.exists()", root).build();
			}
			if (this.tree.isCountProjection()) {
				return builder.addStatement("return $L.count()", root).build();
			}
			if (this.tree.isDelete()) {
				Assert.isTrue(this.context.getMethodReturn().isVoid(),
						String.format("Delete query needs to return void; Offending method: %s", this.queryMethod));
				return builder.addStatement("$L.delete()", root).build();
			}
			String dynamicProjectionParameterName = this.context.getDynamicProjectionParameterName();
			if (dynamicProjectionParameterName != null) {
				builder.add(createDynamicProjectionCodeBlock(root, dynamicProjectionParameterName));
			}
			ReturnedType mappedType = this.context.getReturnedType();
			if (mappedType.needsCustomConstruction() && this.tree.isDistinct()) {
				Collection<String> inputProperties = QueryUtils.getSelectFields(this.mappingContext, mappedType,
						this.tree.isDistinct());
				builder.addStatement("$1L.aggregateFacet($2L)", root, createParameterArray(inputProperties));
			}
			String sortParameterName = this.context.getSortParameterName();
			if (sortParameterName != null) {
				builder.add(createSortCodeBlock(root, sortParameterName));
			}
			String limitParameterName = this.context.getLimitParameterName();
			Integer maxResults = this.tree.getMaxResults();
			// The First/Top keyword always takes precedence over the Limit parameter.
			if (limitParameterName != null && maxResults == null) {
				builder.beginControlFlow("if ($L.isLimited())", limitParameterName);
				builder.addStatement("$1L.limit($2L.max())", root, limitParameterName);
				builder.endControlFlow();
			}
			String pageableParameterName = this.context.getPageableParameterName();
			if (pageableParameterName != null) {
				builder.add(createSortCodeBlock(root, pageableParameterName + ".getSort()"));
				builder.add(createPageableCodeBlock(root, pageableParameterName, maxResults));
			}
			String it = this.context.localVariable("it");
			String reqTotalExpr = this.queryMethod.isPageQuery() ? "$3L.reqTotal()" : "$3L";
			builder.addStatement(
					"$1T<$5T, $6T> $2L = new $1T<>(%s.execute(), %s, $6T.class, $7L)".formatted(reqTotalExpr,
							dynamicProjectionParameterName != null ? "$4L" : "$4T.class"),
					ProjectingResultIterator.class, it, root,
					dynamicProjectionParameterName != null ? dynamicProjectionParameterName
							: mappedType.getReturnedType(),
					mappedType.getReturnedType(), this.context.getDomainType(), "getReindexerConverter()");
			if (this.queryMethod.isPageQuery()) {
				return builder
					.addStatement("return $1T.getPage($2T.toList($3L), $4L, $3L::getTotalCount)",
							PageableExecutionUtils.class, ReindexerQueryExecutions.class, it, pageableParameterName)
					.build();
			}
			if (this.queryMethod.isSliceQuery()) {
				Assert.notNull(pageableParameterName, String
					.format("Slice query needs to have a Pageable parameter; Offending method: %s", this.queryMethod));
				return builder
					.addStatement("return $1T.toSlice($2L, $3L)", ReindexerQueryExecutions.class, it,
							pageableParameterName)
					.build();
			}
			if (this.queryMethod.isStreamQuery()) {
				return builder.addStatement("return $1T.toStream($2L)", ReindexerQueryExecutions.class, it).build();
			}
			if (this.queryMethod.isCollectionQuery()) {
				return builder
					.addStatement("return ($1T) $2T.toCollection($3L, $1T.class)",
							this.context.getMethodReturn().toClass(), ReindexerQueryExecutions.class, it)
					.build();
			}
			if (this.queryMethod.isIteratorQuery()) {
				return builder.addStatement("return $L", it).build();
			}
			if (this.context.getMethodReturn().isOptional()) {
				return builder
					.addStatement("return $1T.ofNullable($2T.toEntity($3L))", Optional.class,
							ReindexerQueryExecutions.class, it)
					.build();
			}
			return builder.addStatement("return $1T.toEntity($2L)", ReindexerQueryExecutions.class, it).build();
		}

		private CodeBlock createDynamicProjectionCodeBlock(String root, String dynamicProjectionParameterName) {
			CodeBlock.Builder builder = CodeBlock.builder();
			String returnedType = this.context.localVariable("returnedType");
			builder.addStatement("$1T $2L = $1T.of($3L, $4T.class, getProjectionFactory())", ReturnedType.class,
					returnedType, dynamicProjectionParameterName, this.context.getDomainType());
			builder.beginControlFlow("if ($L.needsCustomConstruction())", returnedType);
			String fields = this.context.localVariable("fields");
			builder.addStatement(
					"String[] $1L = $2T.getSelectFields(getMappingContext(), $3L, $4L).toArray(String[]::new)", fields,
					QueryUtils.class, returnedType, this.tree.isDistinct());
			if (this.tree.isDistinct()) {
				String field = this.context.localVariable("field");
				builder.beginControlFlow("for (String $1L : $2L)", field, fields);
				builder.addStatement("$1L.aggregateDistinct($2L)", root, field);
				builder.endControlFlow();
				builder.addStatement("$1L.aggregateFacet($2L)", root, fields);
			}
			else {
				builder.addStatement("$1L.select($2L)", root, fields);
			}
			builder.endControlFlow();
			return builder.build();
		}

		private CodeBlock createSortCodeBlock(String root, String sortParameterName) {
			CodeBlock.Builder builder = CodeBlock.builder();
			String order = this.context.localVariable("order");
			builder.beginControlFlow("if ($L.isSorted())", sortParameterName);
			builder.beginControlFlow("for ($1T $2L: $3L)", Sort.Order.class, order, sortParameterName);
			builder.addStatement("$1L.sort($2L.getProperty(), $2L.isDescending())", root, order);
			builder.endControlFlow();
			builder.endControlFlow();
			return builder.build();
		}

		private CodeBlock createPageableCodeBlock(String root, String pageableParameterName, Integer maxResults) {
			CodeBlock.Builder builder = CodeBlock.builder();
			builder.beginControlFlow("if ($L.isPaged())", pageableParameterName);
			if (maxResults != null) {
				String firstResult = this.context.localVariable("firstResult");
				builder.addStatement("int $1L = $2T.getOffsetAsInteger($3L)", firstResult, PageableUtils.class,
						pageableParameterName);
				/*
				 * In order to return the correct results, we have to adjust the first
				 * result offset to be returned if: - a Pageable parameter is present -
				 * AND the requested page number > 0 - AND the requested page size was
				 * bigger than the derived result limitation via the First/Top keyword.
				 */
				builder.addStatement(
						"$1L.offset($2L.getPageSize() > $3L && $4L > 0 ? $4L - ($2L.getPageSize() - $3L) : $4L)", root,
						pageableParameterName, maxResults, firstResult);
			}
			else {
				String limitExpr = this.queryMethod.isSliceQuery() ? "$2L.getPageSize() + 1" : "$2L.getPageSize()";
				builder.addStatement("$1L.limit(%s).offset($3T.getOffsetAsInteger($2L))".formatted(limitExpr), root,
						pageableParameterName, PageableUtils.class);
			}
			builder.endControlFlow();
			return builder.build();
		}

	}

	private static CodeBlock createParameterArray(Collection<String> inputProperties) {
		CodeBlock.Builder builder = CodeBlock.builder();
		for (Iterator<String> iterator = inputProperties.iterator(); iterator.hasNext();) {
			String inputProperty = iterator.next();
			builder.add("$S", inputProperty);
			if (iterator.hasNext()) {
				builder.add(", ");
			}
		}
		return builder.build();
	}

	private ReindexerCodeBlocks() {
	}

}
