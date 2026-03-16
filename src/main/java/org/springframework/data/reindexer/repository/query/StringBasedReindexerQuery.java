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
package org.springframework.data.reindexer.repository.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.vector.params.KnnSearchParam;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.domain.Vector;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.repository.util.PageableUtils;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter.QueryExpressionEvaluator;
import org.springframework.data.util.Lazy;
import org.springframework.util.Assert;

/**
 * A visitor-based implementation that uses {@link net.sf.jsqlparser.parser.CCJSqlParser}
 * to parse a string query, this is considered for more advanced use when extra
 * type-safety is required for working with string queries.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class StringBasedReindexerQuery extends AbstractReindexerQuery {

	private static final ReindexerColumnResolvingExpressionVisitor COLUMN_RESOLVING_VISITOR = new ReindexerColumnResolvingExpressionVisitor();

	private final ReindexerQueryMethod method;

	private final ReindexerConverter reindexerConverter;

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final QueryExpressionEvaluator queryEvaluator;

	private final Statement statement;

	private final Map<String, Integer> namedParameters;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param accessor the {@link QueryMethodValueEvaluationContextAccessor} to use
	 */
	public StringBasedReindexerQuery(ReindexerQueryMethod method, ReindexerConverter reindexerConverter,
			Reindexer reindexer, ReindexerMappingContext mappingContext,
			QueryMethodValueEvaluationContextAccessor accessor) {
		super(method, reindexerConverter);
		this.method = method;
		this.reindexerConverter = reindexerConverter;
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		ValueExpressionQueryRewriter queryRewriter = ValueExpressionQueryRewriter.of(ValueExpressionParser.create(),
				(index, expression) -> "__$synthetic$__" + index, (prefix, name) -> ":" + name);
		this.queryEvaluator = queryRewriter.withEvaluationContextAccessor(accessor)
			.parse(method.getQuery(), method.getParameters());
		this.statement = parseStatement(this.queryEvaluator.getQueryString());
		this.namedParameters = getNamedParameters(method);
	}

	ReindexerQuery createQuery(ReindexerParameterAccessor parameterAccessor, ReturnedType returnedType) {
		ReindexerStatementVisitor statementVisitor = new ReindexerStatementVisitor(parameterAccessor);
		Query<?> criteria = this.statement.accept(statementVisitor, null);
		return new ReindexerQuery(criteria, returnedType, parameterAccessor);
	}

	@Override
	Function<ReindexerQuery, Object> getQueryExecution(ReindexerQueryMethod method) {
		ReindexerQueryExecutionResolvingVisitor visitor = new ReindexerQueryExecutionResolvingVisitor();
		return this.statement.accept(visitor, null);
	}

	private ReindexerNamespace<?> openNamespace(String namespaceName) {
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(namespaceName);
		Namespace<?> namespace = this.reindexer.openNamespace(entity.getNamespace(), entity.getNamespaceOptions(),
				entity.getType());
		return (ReindexerNamespace<?>) namespace;
	}

	private QueryParameterMapper createParameterMapper(ReindexerNamespace<?> namespace) {
		return new QueryParameterMapper(namespace.getItemClass(), this.mappingContext, this.reindexerConverter);
	}

	private Map<String, Integer> getNamedParameters(ReindexerQueryMethod method) {
		Map<String, Integer> namedParameters = new HashMap<>();
		for (ReindexerParameter parameter : method.getParameters()) {
			if (parameter.isNamedParameter()) {
				parameter.getName().ifPresent(name -> namedParameters.put(name, parameter.getIndex()));
			}
		}
		// Add Vector and KnnSearchParam parameters to named parameters.
		if (method.getParameters().hasVectorParameter()) {
			ReindexerParameter parameter = method.getParameters().getParameter(method.getParameters().getVectorIndex());
			parameter.getName().ifPresent(name -> namedParameters.put(name, parameter.getIndex()));
		}
		if (method.getParameters().hasKnnSearchParam()) {
			ReindexerParameter parameter = method.getParameters()
				.getParameter(method.getParameters().getKnnSearchParamIndex());
			parameter.getName().ifPresent(name -> namedParameters.put(name, parameter.getIndex()));
		}
		return Collections.unmodifiableMap(namedParameters);
	}

	private static Statement parseStatement(String query) {
		try {
			return CCJSqlParserUtil.parse(query);
		}
		catch (JSQLParserException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private final class ReindexerQueryExecutionResolvingVisitor
			extends StatementVisitorAdapter<Function<ReindexerQuery, Object>> {

		@Override
		public <S> Function<ReindexerQuery, Object> visit(Select select, S context) {
			ReindexerSelectQueryExecutionResolvingVisitor selectResolvingVisitor = new ReindexerSelectQueryExecutionResolvingVisitor();
			return select.accept(selectResolvingVisitor, context);
		}

		@Override
		public <S> Function<ReindexerQuery, Object> visit(Update update, S context) {
			return (query) -> {
				query.criteria().update();
				return null;
			};
		}

		@Override
		public <S> Function<ReindexerQuery, Object> visit(Delete delete, S context) {
			return (query) -> {
				query.criteria().delete();
				return null;
			};
		}

	}

	private final class ReindexerSelectQueryExecutionResolvingVisitor
			extends SelectVisitorAdapter<Function<ReindexerQuery, Object>> {

		@Override
		public <S> Function<ReindexerQuery, Object> visit(SetOperationList setOpList, S context) {
			return StringBasedReindexerQuery.super.getQueryExecution(StringBasedReindexerQuery.this.method);
		}

		@Override
		public <S> Function<ReindexerQuery, Object> visit(PlainSelect plainSelect, S context) {
			if (plainSelect.getSelectItems().size() == 1) {
				SelectItem<?> selectItem = plainSelect.getSelectItem(0);
				Expression expr = selectItem.getExpression();
				Function<ReindexerQuery, Object> queryExecution = expr
					.accept(new ReindexerSelectItemQueryExecutionResolvingVisitor(), context);
				if (queryExecution != null) {
					return queryExecution;
				}
			}
			return StringBasedReindexerQuery.super.getQueryExecution(StringBasedReindexerQuery.this.method);
		}

	}

	private final class ReindexerSelectItemQueryExecutionResolvingVisitor
			extends ExpressionVisitorAdapter<Function<ReindexerQuery, Object>> {

		@Override
		public <S> Function<ReindexerQuery, Object> visit(net.sf.jsqlparser.expression.Function function, S context) {
			String functionName = function.getName().toLowerCase(Locale.ROOT);
			return switch (functionName) {
				case "count", "count_cached" -> (query) -> query.criteria().count();
				case "sum", "min", "max", "avg" -> (query) -> {
					try (ReindexerResultAccessor<?> it = toResultAccessor(query)) {
						return it.aggregationValue(functionName,
								COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(function));
					}
				};
				default -> StringBasedReindexerQuery.super.getQueryExecution(StringBasedReindexerQuery.this.method);
			};
		}

	}

	private final class ReindexerStatementVisitor extends StatementVisitorAdapter<Query<?>> {

		private final Lazy<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor;

		private final Lazy<SelectVisitor<Query<?>>> selectVisitor;

		private ReindexerStatementVisitor(ReindexerParameterAccessor parameterAccessor) {
			this.valueResolvingVisitor = Lazy.of(() -> {
				Map<String, Object> resolvedParameters = StringBasedReindexerQuery.this.queryEvaluator
					.evaluate(parameterAccessor.getValues());
				ReindexerParameterResolver parameterResolver = new ReindexerParameterResolver(resolvedParameters,
						parameterAccessor);
				return new ReindexerValueResolvingExpressionVisitor(parameterResolver);
			});
			this.selectVisitor = Lazy
				.of(() -> new ReindexerSelectVisitor(parameterAccessor, this.valueResolvingVisitor));
		}

		@Override
		public <S> Query<?> visit(Select select, S ctx) {
			return select.accept(this.selectVisitor.get(), ctx);
		}

		@Override
		public <S> Query<?> visit(Update update, S ctx) {
			ReindexerNamespace<?> namespace = openNamespace(update.getTable().getName());
			QueryParameterMapper parameterMapper = createParameterMapper(namespace);
			Query<?> criteria = namespace.query();
			for (UpdateSet updateSet : update.getUpdateSets()) {
				List<Column> columns = updateSet.getColumns();
				for (int i = 0; i < columns.size(); i++) {
					Column column = columns.get(i);
					Object value = parameterMapper.mapParameterValue(column.getColumnName(),
							this.valueResolvingVisitor.get().resolveValue(updateSet.getValue(i)));
					criteria.set(column.getColumnName(), value);
				}
			}
			if (update.getWhere() != null) {
				ReindexerConditionalExpressionVisitor conditionalVisitor = new ReindexerConditionalExpressionVisitor(
						criteria, () -> parameterMapper, this.valueResolvingVisitor, this.selectVisitor.get());
				update.getWhere().accept(conditionalVisitor, new ConditionContext());
			}
			return criteria;
		}

		@Override
		public <S> Query<?> visit(Delete delete, S context) {
			ReindexerNamespace<?> namespace = openNamespace(delete.getTable().getName());
			Query<?> criteria = namespace.query();
			if (delete.getWhere() != null) {
				ReindexerConditionalExpressionVisitor conditionalVisitor = new ReindexerConditionalExpressionVisitor(
						criteria, Lazy.of(() -> createParameterMapper(namespace)), this.valueResolvingVisitor,
						this.selectVisitor.get());
				delete.getWhere().accept(conditionalVisitor, new ConditionContext());
			}
			return criteria;
		}

	}

	private final class ReindexerSelectVisitor extends SelectVisitorAdapter<Query<?>> {

		private final ReindexerParameterAccessor parameterAccessor;

		private final Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor;

		private ReindexerSelectVisitor(ReindexerParameterAccessor parameterAccessor,
				Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor) {
			this.parameterAccessor = parameterAccessor;
			this.valueResolvingVisitor = valueResolvingVisitor;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S> Query<?> visit(SetOperationList setOpList, S context) {
			Query<Object> root = null;
			for (Select select : setOpList.getSelects()) {
				Query<Object> query = (Query<Object>) select.accept(this, context);
				root = root == null ? query : root.merge(query);
			}
			Assert.notNull(root, () -> "Could not resolve a root from: " + setOpList);
			return root;
		}

		@Override
		public <S> Query<?> visit(ParenthesedSelect parenthesedSelect, S context) {
			return parenthesedSelect.getSelect().accept(this, context);
		}

		@Override
		public <S> Query<?> visit(PlainSelect plainSelect, S context) {
			Table table = (Table) plainSelect.getFromItem();
			ReindexerNamespace<?> namespace = openNamespace(table.getName());
			Query<?> root = namespace.query();
			// Apply select.
			ReindexerSelectItemExpressionVisitor selectItemVisitor = new ReindexerSelectItemExpressionVisitor(root,
					this.valueResolvingVisitor);
			plainSelect.getSelectItems().forEach(item -> item.accept(selectItemVisitor, context));
			// Apply joins.
			if (plainSelect.getJoins() != null) {
				for (Join join : plainSelect.getJoins()) {
					Table joinTable = (Table) join.getFromItem();
					ReindexerNamespace<?> joinNamespace = openNamespace(joinTable.getName());
					Query<?> joinQuery = joinNamespace.query();
					ReindexerJoinOnExpressionVisitor joinOnVisitor = new ReindexerJoinOnExpressionVisitor(joinQuery,
							Lazy.of(() -> createParameterMapper(joinNamespace)), this.valueResolvingVisitor, this);
					// Reindexer does not support joining namespaces whose parent is not a
					// root namespace, therefore, the root namespace is always passed as a
					// parent table to the visitor's context.
					JoinConditionContext ctx = JoinConditionContext.of(table, joinTable);
					// Join must contain at least one ON expression.
					join.getOnExpressions().forEach(expr -> expr.accept(joinOnVisitor, ctx));
					// Map joined table alias to the joined entity field.
					String joinField = joinTable.getAlias() != null ? joinTable.getAlias().getName()
							: joinTable.getName();
					if (join.isInner()) {
						root.innerJoin(joinQuery, joinField);
					}
					else if (join.isLeft()) {
						root.leftJoin(joinQuery, joinField);
					}
					else {
						throw new InvalidDataAccessApiUsageException("Unsupported join: " + join);
					}
				}
			}
			// Apply where.
			if (plainSelect.getWhere() != null) {
				ReindexerConditionalExpressionVisitor conditionalVisitor = new ReindexerConditionalExpressionVisitor(
						root, Lazy.of(() -> createParameterMapper(namespace)), this.valueResolvingVisitor, this);
				plainSelect.getWhere().accept(conditionalVisitor, new ConditionContext());
			}
			// Apply paging.
			Pageable pageable = this.parameterAccessor.getPageable();
			if (pageable.isPaged()) {
				int limit = StringBasedReindexerQuery.this.method.isSliceQuery() ? pageable.getPageSize() + 1
						: pageable.getPageSize();
				root.limit(limit).offset(PageableUtils.getOffsetAsInteger(pageable));
			}
			// Apply sorting.
			if (plainSelect.getOrderByElements() != null) {
				for (OrderByElement order : plainSelect.getOrderByElements()) {
					String indexName = COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(order.getExpression());
					root.sort(indexName, !order.isAsc());
				}
			}
			Sort sort = this.parameterAccessor.getSort();
			if (sort.isSorted()) {
				for (Order order : sort) {
					root.sort(order.getProperty(), order.isDescending());
				}
			}
			// Apply limit/offset.
			Limit limit = plainSelect.getLimit();
			if (limit != null) {
				if (limit.getRowCount() != null) {
					int rowCount = this.valueResolvingVisitor.get().resolveNumberValue(limit.getRowCount()).intValue();
					if (pageable.isPaged()) {
						/*
						 * In order to return the correct results, we have to adjust the
						 * first result offset to be returned if: - a Pageable parameter
						 * is present - AND the requested page number > 0 - AND the
						 * requested page size was bigger than the derived result
						 * limitation via the limit keyword.
						 */
						int firstResult = PageableUtils.getOffsetAsInteger(pageable);
						if (pageable.getPageSize() > rowCount && firstResult > 0) {
							root.offset(firstResult - (pageable.getPageSize() - rowCount));
						}
					}
					root.limit(rowCount);
				}
				if (limit.getOffset() != null) {
					int offset = this.valueResolvingVisitor.get().resolveNumberValue(limit.getOffset()).intValue();
					root.offset(offset);
				}
			}
			if (plainSelect.getGroupBy() != null) {
				throw new InvalidDataAccessApiUsageException("GROUP BY expression is not supported");
			}
			return root;
		}

	}

	private static final class ReindexerSelectItemExpressionVisitor extends ExpressionVisitorAdapter<Query<?>> {

		private final Query<?> root;

		private final Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor;

		private ReindexerSelectItemExpressionVisitor(Query<?> root,
				Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor) {
			this.root = root;
			this.valueResolvingVisitor = valueResolvingVisitor;
		}

		@Override
		public <S> Query<?> visit(Column column, S context) {
			return this.root.select(column.getColumnName());
		}

		@Override
		public <C> Query<?> visit(net.sf.jsqlparser.expression.Function function, C context) {
			String functionName = function.getName().toLowerCase(Locale.ROOT);
			return switch (functionName) {
				case "count", "count_cached" -> this.root.reqTotal();
				case "vectors" -> this.root.select("vectors()");
				case "rank" -> this.root.withRank();
				case "sum" -> this.root.aggregateSum(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(function));
				case "min" -> this.root.aggregateMin(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(function));
				case "max" -> this.root.aggregateMax(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(function));
				case "avg" -> this.root.aggregateAvg(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(function));
				case "facet" -> applyAggregateFacetFunction(function);
				default -> throw new InvalidDataAccessApiUsageException("Invalid function expression: " + function);
			};
		}

		private Query<?> applyAggregateFacetFunction(net.sf.jsqlparser.expression.Function facet) {
			if (facet != null) {
				List<String> facetFields = new ArrayList<>();
				// Apply facet parameters.
				if (facet.getParameters() != null) {
					for (Expression parameter : facet.getParameters()) {
						Column facetColumn = COLUMN_RESOLVING_VISITOR.resolveColumn(parameter);
						if (facetColumn != null) {
							facetFields.add(facetColumn.getColumnName());
						}
					}
				}
				Query<?>.AggregationFacetRequest facetRequest = this.root
					.aggregateFacet(facetFields.toArray(String[]::new));
				// Apply facet sorting.
				if (facet.getOrderByElements() != null) {
					for (OrderByElement order : facet.getOrderByElements()) {
						String indexName = COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(order.getExpression());
						facetRequest.sort(indexName, !order.isAsc());
					}
				}
				// Apply facet limit/offset.
				if (facet.getLimit() != null) {
					if (facet.getLimit().getRowCount() != null) {
						int rowCount = this.valueResolvingVisitor.get()
							.resolveNumberValue(facet.getLimit().getRowCount())
							.intValue();
						facetRequest.limit(rowCount);
					}
					if (facet.getLimit().getOffset() != null) {
						int offset = this.valueResolvingVisitor.get()
							.resolveNumberValue(facet.getLimit().getOffset())
							.intValue();
						facetRequest.offset(offset);
					}
				}
			}
			return this.root;
		}

	}

	private class ReindexerConditionalExpressionVisitor extends ExpressionVisitorAdapter<Query<?>> {

		final Query<?> criteria;

		private final Supplier<QueryParameterMapper> parameterMapper;

		private final Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor;

		private final SelectVisitor<Query<?>> selectVisitor;

		private ReindexerConditionalExpressionVisitor(Query<?> criteria, Supplier<QueryParameterMapper> parameterMapper,
				Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor,
				SelectVisitor<Query<?>> selectVisitor) {
			this.criteria = criteria;
			this.parameterMapper = parameterMapper;
			this.valueResolvingVisitor = valueResolvingVisitor;
			this.selectVisitor = selectVisitor;
		}

		@Override
		public <S> Query<?> visit(Column column, S context) {
			throw new InvalidDataAccessApiUsageException("""
					Invalid expression: bare column: '%s' is used as a predicate,
					conditional operator must be used (e.g., =, IN, IS NULL).""".formatted(column));
		}

		@Override
		public <S> Query<?> visit(Select select, S context) {
			return select.accept(this.selectVisitor, context);
		}

		@Override
		public <S> Query<?> visit(AndExpression expr, S ctx) {
			ConditionContext context = getConditionContext(ctx);
			Expression previous = context.parent;
			context.parent = expr;
			expr.getLeftExpression().accept(this, ctx);
			expr.getRightExpression().accept(this, ctx);
			context.parent = previous;
			return this.criteria;
		}

		@Override
		public <S> Query<?> visit(OrExpression expr, S ctx) {
			ConditionContext context = getConditionContext(ctx);
			boolean needsBracket = context.parent instanceof AndExpression;
			if (needsBracket) {
				this.criteria.openBracket();
			}
			Expression previous = context.parent;
			context.parent = expr;
			expr.getLeftExpression().accept(this, ctx);
			this.criteria.or();
			expr.getRightExpression().accept(this, ctx);
			context.parent = previous;
			if (needsBracket) {
				this.criteria.closeBracket();
			}
			return this.criteria;
		}

		@Override
		public <S> Query<?> visit(NotExpression notExpr, S ctx) {
			this.criteria.not();
			return notExpr.getExpression().accept(this, ctx);
		}

		@Override
		public <S> Query<?> visit(GreaterThan expr, S ctx) {
			return buildBinaryCondition(expr, Condition.GT, ctx);
		}

		@Override
		public <S> Query<?> visit(GreaterThanEquals expr, S ctx) {
			return buildBinaryCondition(expr, Condition.GE, ctx);
		}

		@Override
		public <S> Query<?> visit(MinorThan expr, S ctx) {
			return buildBinaryCondition(expr, Condition.LT, ctx);
		}

		@Override
		public <S> Query<?> visit(MinorThanEquals expr, S ctx) {
			return buildBinaryCondition(expr, Condition.LE, ctx);
		}

		@Override
		public <S> Query<?> visit(EqualsTo expr, S ctx) {
			return buildBinaryCondition(expr, Condition.EQ, ctx);
		}

		@Override
		public <S> Query<?> visit(InExpression expr, S ctx) {
			if (expr.isNot()) {
				this.criteria.not();
			}
			Expression left = expr.getLeftExpression();
			Expression right = expr.getRightExpression();
			return buildComparisonCondition(left, right, Condition.SET, getConditionContext(ctx));
		}

		@Override
		public <S> Query<?> visit(IsNullExpression expr, S ctx) {
			Condition condition = expr.isNot() ? Condition.ANY : Condition.EMPTY;
			Column leftColumn = COLUMN_RESOLVING_VISITOR.resolveColumn(expr.getLeftExpression());
			if (leftColumn != null) {
				return this.criteria.where(leftColumn.getColumnName(), condition);
			}
			Query<?> leftQuery = expr.getLeftExpression().accept(this, ctx);
			if (isSubQuery(leftQuery)) {
				return this.criteria.where(leftQuery, condition);
			}
			throw new InvalidDataAccessApiUsageException(
					"Invalid expression: %s expected column or sub query in the left operand".formatted(expr));
		}

		@Override
		public <S> Query<?> visit(Between expr, S ctx) {
			if (expr.isNot()) {
				this.criteria.not();
			}
			return buildRangeCondition(expr.getLeftExpression(), expr.getBetweenExpressionStart(),
					expr.getBetweenExpressionEnd(), ctx);
		}

		@Override
		public <S> Query<?> visit(net.sf.jsqlparser.expression.Function function, S ctx) {
			String functionName = function.getName().toLowerCase(Locale.ROOT);
			return switch (functionName) {
				case "range" -> {
					Assert.isTrue(function.getParameters() != null && function.getParameters().size() == 3,
							() -> "Expected exactly 3 parameters for: " + function);
					yield buildRangeCondition(function.getParameters().get(0), function.getParameters().get(1),
							function.getParameters().get(2), ctx);
				}
				case "knn" -> {
					Assert.isTrue(function.getParameters() != null && function.getParameters().size() == 3,
							() -> "Expected exactly 3 parameters for: " + function);
					String indexName = COLUMN_RESOLVING_VISITOR
						.resolveRequiredIndexName(function.getParameters().get(0));
					ReindexerValueResolvingExpressionVisitor valueResolvingVisitor = this.valueResolvingVisitor.get();
					float[] vector = valueResolvingVisitor.resolveVector(function.getParameters().get(1));
					KnnSearchParam knnSearchParam = valueResolvingVisitor
						.resolveKnnSearchParam(function.getParameters().get(2));
					yield this.criteria.whereKnn(indexName, vector, knnSearchParam);
				}
				default -> super.visit(function, ctx);
			};
		}

		private <S> Query<?> buildBinaryCondition(BinaryExpression expr, Condition condition, S ctx) {
			Expression left = expr.getLeftExpression();
			Expression right = expr.getRightExpression();
			return buildComparisonCondition(left, right, condition, ctx);
		}

		private <S> Query<?> buildComparisonCondition(Expression left, Expression right, Condition condition, S ctx) {
			Column leftColumn = COLUMN_RESOLVING_VISITOR.resolveColumn(left);
			Column rightColumn = COLUMN_RESOLVING_VISITOR.resolveColumn(right);
			if (leftColumn != null && rightColumn != null) {
				return this.criteria.whereBetweenFields(leftColumn.getColumnName(), condition,
						rightColumn.getColumnName());
			}
			if (leftColumn != null) {
				return buildComparisonCondition(leftColumn, condition, right);
			}
			if (rightColumn != null) {
				return buildComparisonCondition(rightColumn, condition, left);
			}
			Query<?> leftQuery = left.accept(this, ctx);
			if (isSubQuery(leftQuery)) {
				Object value = this.valueResolvingVisitor.get().resolveRequiredValue(right);
				return this.criteria.where(leftQuery, condition, value);
			}
			Query<?> rightQuery = right.accept(this, ctx);
			if (isSubQuery(rightQuery)) {
				Object value = this.valueResolvingVisitor.get().resolveRequiredValue(left);
				return this.criteria.where(rightQuery, condition, value);
			}
			throw new InvalidDataAccessApiUsageException(
					"Invalid operand combination: %s, %s for condition: %s".formatted(left, right, condition));
		}

		private Query<?> buildComparisonCondition(Column column, Condition condition, Expression expr) {
			String columnName = column.getColumnName();
			Object value = this.parameterMapper.get()
				.mapParameterValue(columnName, this.valueResolvingVisitor.get().resolveRequiredValue(expr));
			if (value instanceof Collection<?> values) {
				return this.criteria.where(columnName, condition, values);
			}
			return this.criteria.where(columnName, condition, value);
		}

		private <S> Query<?> buildRangeCondition(Expression left, Expression rangeStart, Expression rangeEnd, S ctx) {
			ReindexerValueResolvingExpressionVisitor valueResolvingVisitor = this.valueResolvingVisitor.get();
			Object start = valueResolvingVisitor.resolveRequiredValue(rangeStart);
			Object end = valueResolvingVisitor.resolveRequiredValue(rangeEnd);
			Column column = COLUMN_RESOLVING_VISITOR.resolveColumn(left);
			if (column != null) {
				return this.criteria.where(column.getColumnName(), Condition.RANGE,
						this.parameterMapper.get().mapParameterValues(column.getColumnName(), start, end));
			}
			Query<?> leftQuery = left.accept(this, ctx);
			if (isSubQuery(leftQuery)) {
				return this.criteria.where(leftQuery, Condition.RANGE, start, end);
			}
			throw new InvalidDataAccessApiUsageException(
					"Invalid left operand: %s for RANGE condition, expected column or sub-query".formatted(left));
		}

		private boolean isSubQuery(Query<?> query) {
			return query != null && query != this.criteria;
		}

		<S> ConditionContext getConditionContext(S ctx) {
			if (ctx instanceof ConditionContext context) {
				return context;
			}
			throw new IllegalArgumentException("Unexpected context: " + ctx);
		}

	}

	private final class ReindexerJoinOnExpressionVisitor extends ReindexerConditionalExpressionVisitor {

		private ReindexerJoinOnExpressionVisitor(Query<?> query, Supplier<QueryParameterMapper> parameterMapper,
				Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor,
				SelectVisitor<Query<?>> selectVisitor) {
			super(query, parameterMapper, valueResolvingVisitor, selectVisitor);
		}

		@Override
		public <S> Query<?> visit(EqualsTo expr, S ctx) {
			JoinConditionContext context = getConditionContext(ctx);
			Column left = COLUMN_RESOLVING_VISITOR.resolveColumn(expr.getLeftExpression());
			Column right = COLUMN_RESOLVING_VISITOR.resolveColumn(expr.getRightExpression());
			if (left != null && right != null) {
				return buildOnCondition(left, right, Condition.EQ, context);
			}
			return super.visit(expr, ctx);
		}

		@Override
		public <S> Query<?> visit(InExpression expr, S ctx) {
			JoinConditionContext context = getConditionContext(ctx);
			Column left = COLUMN_RESOLVING_VISITOR.resolveColumn(expr.getLeftExpression());
			Column right = COLUMN_RESOLVING_VISITOR.resolveColumn(expr.getRightExpression());
			if (left != null && right != null) {
				return buildOnCondition(left, right, Condition.SET, context);
			}
			return super.visit(expr, ctx);
		}

		private Query<?> buildOnCondition(Column left, Column right, Condition condition,
				JoinConditionContext context) {
			String leftOwner = extractOwner(left);
			String rightOwner = extractOwner(right);
			if (context.isParent(leftOwner) && context.isChild(rightOwner)) {
				return this.criteria.on(left.getColumnName(), condition, right.getColumnName());
			}
			else if (context.isChild(leftOwner) && context.isParent(rightOwner)) {
				return this.criteria.on(right.getColumnName(), condition, left.getColumnName());
			}
			else {
				throw new InvalidDataAccessApiUsageException("""
						Unexpected tables to join: (%s, %s);
						The parent's and child's aliases: (%s) must be used to join tables""".formatted(leftOwner,
						rightOwner, String.join(", ", context.tableRoles.keySet())));
			}
		}

		private String extractOwner(Column column) {
			Table table = column.getTable();
			if (table == null) {
				throw new InvalidDataAccessApiUsageException(
						"Join columns must be qualified with the table name or alias: " + column);
			}
			// Table#getName() is either table name or alias used within the ON.
			return table.getName().toLowerCase();
		}

		<S> JoinConditionContext getConditionContext(S ctx) {
			if (ctx instanceof JoinConditionContext) {
				return (JoinConditionContext) ctx;
			}
			throw new IllegalArgumentException("Unexpected context: " + ctx);
		}

	}

	private static final class ReindexerColumnResolvingExpressionVisitor extends ExpressionVisitorAdapter<Column> {

		@Override
		public <S> Column visit(Column column, S ctx) {
			return column;
		}

		@Override
		public <S> Column visit(net.sf.jsqlparser.expression.Function function, S context) {
			if (function.getParameters() != null && function.getParameters().size() == 1) {
				return function.getParameters().get(0).accept(this, context);
			}
			throw new InvalidDataAccessApiUsageException("Expected exactly one parameter for function: " + function);
		}

		@Override
		public <S> Column visit(ExpressionList<? extends Expression> expressionList, S context) {
			// Unwraps any parenthesis expression (a) or ((b)) recursively.
			if (expressionList.size() == 1) {
				return expressionList.get(0).accept(this, context);
			}
			// Multi-column expressions (a, b) = (c, d) are not supported by the visitor.
			throw new InvalidDataAccessApiUsageException("""
					Invalid expression: %s;
					Multi-column expressions e.g., (a, b) = (c, d) are not supported
					""".formatted(expressionList));
		}

		@Override
		public <S> Column visit(CastExpression castExpression, S context) {
			return castExpression.getLeftExpression().accept(this, context);
		}

		private String resolveRequiredIndexName(Expression expr) {
			Column column = resolveColumn(expr);
			Assert.notNull(column, () -> "Could not resolve a column for expression: " + expr);
			return column.getColumnName();
		}

		private Column resolveColumn(Expression expr) {
			return expr.accept(this, null);
		}

	}

	private final class ReindexerValueResolvingExpressionVisitor extends ExpressionVisitorAdapter<Object> {

		private final ReindexerParameterResolver parameterResolver;

		private ReindexerValueResolvingExpressionVisitor(ReindexerParameterResolver parameterResolver) {
			this.parameterResolver = parameterResolver;
		}

		@Override
		public <S> Object visit(JdbcParameter parameter, S ctx) {
			return this.parameterResolver.resolveIndexed(parameter.getIndex() - 1);
		}

		@Override
		public <S> Object visit(JdbcNamedParameter parameter, S ctx) {
			return this.parameterResolver.resolvedNamed(parameter.getName());
		}

		@Override
		public <S> Object visit(DoubleValue value, S ctx) {
			return value.getValue();
		}

		@Override
		public <S> Object visit(LongValue value, S ctx) {
			return value.getValue();
		}

		@Override
		public <S> Object visit(StringValue value, S ctx) {
			return value.getValue();
		}

		private float[] resolveVector(Expression expr) {
			Object value = resolveValue(expr);
			if (value instanceof float[] vector) {
				return vector;
			}
			if (value instanceof Vector vector) {
				return vector.toFloatArray();
			}
			throw new InvalidDataAccessApiUsageException("""
					Invalid Vector expression: %s;
					Could not resolve Vector or float[] from: %s
					""".formatted(expr, value));
		}

		private KnnSearchParam resolveKnnSearchParam(Expression expr) {
			Object value = resolveValue(expr);
			if (value instanceof KnnSearchParam knnSearchParam) {
				return knnSearchParam;
			}
			throw new InvalidDataAccessApiUsageException("""
					Invalid KNN params expression: %s;
					Could not resolve KnnSearchParam from: %s
					""".formatted(expr, value));
		}

		private Number resolveNumberValue(Expression expr) {
			Object value = resolveValue(expr);
			Assert.isInstanceOf(Number.class, value, () -> "Expected Number value for expression: " + expr);
			return (Number) value;
		}

		private Object resolveRequiredValue(Expression expr) {
			Object value = resolveValue(expr);
			Assert.notNull(value, () -> "Could not resolve value for expression: " + expr);
			return value;
		}

		private Object resolveValue(Expression expr) {
			return expr.accept(this, null);
		}

	}

	private final class ReindexerParameterResolver {

		private final Map<String, Object> resolvedParameters;

		private final ReindexerParameterAccessor parameterAccessor;

		private ReindexerParameterResolver(Map<String, Object> resolvedParameters,
				ReindexerParameterAccessor parameterAccessor) {
			this.resolvedParameters = resolvedParameters;
			this.parameterAccessor = parameterAccessor;
		}

		private Object resolvedNamed(String name) {
			if (this.resolvedParameters.containsKey(name)) {
				return this.resolvedParameters.get(name);
			}
			Integer index = StringBasedReindexerQuery.this.namedParameters.get(name);
			Assert.notNull(index, () -> "Could not resolve parameter: " + name);
			return this.parameterAccessor.getValue(index);
		}

		private Object resolveIndexed(int index) {
			return this.parameterAccessor.getValue(index);
		}

	}

	private static class ConditionContext {

		private Expression parent;

	}

	private static final class JoinConditionContext extends ConditionContext {

		private final Map<String, JoinRole> tableRoles;

		private JoinConditionContext(Map<String, JoinRole> tableRoles) {
			this.tableRoles = tableRoles;
		}

		private static JoinConditionContext of(Table parent, Table child) {
			Map<String, JoinRole> result = new HashMap<>();
			result.put(parent.getName().toLowerCase(), JoinRole.PARENT);
			result.put(child.getName().toLowerCase(), JoinRole.CHILD);
			if (parent.getAlias() != null) {
				result.put(parent.getAlias().getName().toLowerCase(), JoinRole.PARENT);
			}
			if (child.getAlias() != null) {
				result.put(child.getAlias().getName().toLowerCase(), JoinRole.CHILD);
			}
			return new JoinConditionContext(Collections.unmodifiableMap(result));
		}

		private boolean isParent(String owner) {
			return this.tableRoles.get(owner) == JoinRole.PARENT;
		}

		private boolean isChild(String owner) {
			return this.tableRoles.get(owner) == JoinRole.CHILD;
		}

	}

	private enum JoinRole {

		PARENT, CHILD

	}

}
