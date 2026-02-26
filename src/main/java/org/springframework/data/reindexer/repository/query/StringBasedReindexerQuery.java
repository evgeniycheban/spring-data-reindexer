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
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
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
import org.springframework.util.ConcurrentLruCache;

/**
 * A visitor-based implementation that uses {@link net.sf.jsqlparser.parser.CCJSqlParser}
 * to parse a string query, this is considered for more advanced use when extra
 * type-safety is required for working with string queries.
 *
 * @author Evgeniy Cheban
 * @since 1.6
 */
public final class StringBasedReindexerQuery extends AbstractReindexerQuery {

	// @formatter:off
	private static final Lazy<UnaryOperator<String>> QUERY_REWRITER = Lazy.of(() -> PatternMatchingQueryRewriter.compose(
			PatternMatchingQueryRewriter.of("(?i)([\\w.]+)\\s+RANGE\\s*\\(", (matcher) -> "RANGE(" + matcher.group(1) + ","),
			PatternMatchingQueryRewriter.of("(?i)\\bMERGE\\s*\\(", (matcher) -> "UNION ALL(")
	));
	// @formatter:on

	private static final ReindexerFunctionResolvingExpressionVisitor FUNCTION_RESOLVING_VISITOR = new ReindexerFunctionResolvingExpressionVisitor();

	private static final ReindexerColumnResolvingExpressionVisitor COLUMN_RESOLVING_VISITOR = new ReindexerColumnResolvingExpressionVisitor();

	private static final ConcurrentLruCache<String, Statement> CACHE = new ConcurrentLruCache<>(64,
			StringBasedReindexerQuery::parseStatement);

	private final ReindexerQueryMethod method;

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final QueryParameterMapper queryParameterMapper;

	private final Lazy<QueryExpressionEvaluator> queryEvaluator;

	private final Lazy<Map<String, Integer>> namedParameters;

	/**
	 * Creates an instance.
	 * @param method the {@link ReindexerQueryMethod} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param queryParameterMapper the {@link QueryParameterMapper} to use
	 * @param accessor the {@link QueryMethodValueEvaluationContextAccessor} to use
	 */
	public StringBasedReindexerQuery(ReindexerQueryMethod method, ReindexerConverter reindexerConverter,
			Reindexer reindexer, ReindexerMappingContext mappingContext, QueryParameterMapper queryParameterMapper,
			QueryMethodValueEvaluationContextAccessor accessor) {
		super(method, reindexerConverter);
		this.method = method;
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.queryParameterMapper = queryParameterMapper;
		this.queryEvaluator = Lazy.of(() -> createQueryEvaluator(method, accessor));
		this.namedParameters = Lazy.of(() -> getNamedParameters(method));
	}

	ReindexerQuery createQuery(ReindexerParameterAccessor parameterAccessor, ReturnedType returnedType) {
		Statement statement = CACHE.get(this.queryEvaluator.get().getQueryString());
		ReindexerStatementVisitor statementVisitor = new ReindexerStatementVisitor(parameterAccessor);
		Query<?> criteria = statement.accept(statementVisitor, null);
		return new ReindexerQuery(criteria, returnedType, parameterAccessor);
	}

	@Override
	Function<ReindexerQuery, Object> getQueryExecution(ReindexerQueryMethod method) {
		Statement statement = CACHE.get(this.queryEvaluator.get().getQueryString());
		ReindexerQueryExecutionResolvingVisitor visitor = new ReindexerQueryExecutionResolvingVisitor();
		return statement.accept(visitor, null);
	}

	private QueryExpressionEvaluator createQueryEvaluator(ReindexerQueryMethod method,
			QueryMethodValueEvaluationContextAccessor accessor) {
		String query = QUERY_REWRITER.get().apply(method.getQuery());
		return PatternMatchingQueryRewriter.REWRITER.withEvaluationContextAccessor(accessor)
			.parse(query, method.getParameters());
	}

	private ReindexerValueResolvingExpressionVisitor createValueResolvingVisitor(QueryExpressionEvaluator evaluator,
			ReindexerParameterAccessor parameterAccessor) {
		Map<String, Object> resolvedParameters = evaluator.evaluate(parameterAccessor.getValues());
		ReindexerParameterResolver parameterResolver = new ReindexerParameterResolver(resolvedParameters,
				parameterAccessor);
		return new ReindexerValueResolvingExpressionVisitor(parameterResolver);
	}

	private Query<?> createQuery(String namespaceName) {
		ReindexerPersistentEntity<?> entity = StringBasedReindexerQuery.this.mappingContext
			.getRequiredPersistentEntity(namespaceName);
		Namespace<?> namespace = StringBasedReindexerQuery.this.reindexer.openNamespace(entity.getNamespace(),
				entity.getNamespaceOptions(), entity.getType());
		return namespace.query();
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

	private static final class PatternMatchingQueryRewriter implements UnaryOperator<String> {

		private static final String EXPRESSION_PARAMETER_KEY_PREFIX = "__$synthetic$__";

		private static final String EXPRESSION_PARAMETER_NAME_PREFIX = ":";

		private static final ValueExpressionQueryRewriter REWRITER = ValueExpressionQueryRewriter.of(
				ValueExpressionParser.create(), (index, expression) -> EXPRESSION_PARAMETER_KEY_PREFIX + index,
				(prefix, name) -> EXPRESSION_PARAMETER_NAME_PREFIX + name);

		private static UnaryOperator<String> of(String pattern, Function<Matcher, String> replacementFunction) {
			return new PatternMatchingQueryRewriter(pattern, replacementFunction);
		}

		@SafeVarargs
		private static UnaryOperator<String> compose(UnaryOperator<String>... delegates) {
			return Stream.of(delegates)
				.reduce(UnaryOperator.identity(), (identity, next) -> (query) -> next.apply(identity.apply(query)));
		}

		private final Pattern pattern;

		private final Function<Matcher, String> replacementFunction;

		private PatternMatchingQueryRewriter(String pattern, Function<Matcher, String> replacementFunction) {
			this.pattern = Pattern.compile(pattern);
			this.replacementFunction = replacementFunction;
		}

		@Override
		public String apply(String query) {
			ValueExpressionQueryRewriter.ParsedQuery parsedQuery = REWRITER.parse(query);
			StringBuilder result = new StringBuilder();
			Matcher matcher = this.pattern.matcher(query);
			while (matcher.find()) {
				if (parsedQuery.isQuoted(matcher.start())) {
					matcher.appendReplacement(result, matcher.group());
					continue;
				}
				matcher.appendReplacement(result, Matcher.quoteReplacement(this.replacementFunction.apply(matcher)));
			}
			matcher.appendTail(result);
			return result.toString();
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
			return getDefaultQueryExecution();
		}

		@Override
		public <S> Function<ReindexerQuery, Object> visit(PlainSelect plainSelect, S context) {
			if (plainSelect.getSelectItems().size() == 1) {
				SelectItem<?> selectItem = plainSelect.getSelectItem(0);
				Expression expr = selectItem.getExpression();
				if (FUNCTION_RESOLVING_VISITOR.resolveCount(expr) != null) {
					return (query) -> query.criteria().count();
				}
				if (expr.accept(FUNCTION_RESOLVING_VISITOR, "sum") != null) {
					return getAggregationQueryExecution("sum", expr);
				}
				if (expr.accept(FUNCTION_RESOLVING_VISITOR, "min") != null) {
					return getAggregationQueryExecution("min", expr);
				}
				if (expr.accept(FUNCTION_RESOLVING_VISITOR, "max") != null) {
					return getAggregationQueryExecution("max", expr);
				}
				if (expr.accept(FUNCTION_RESOLVING_VISITOR, "avg") != null) {
					return getAggregationQueryExecution("avg", expr);
				}
			}
			return getDefaultQueryExecution();
		}

		private Function<ReindexerQuery, Object> getAggregationQueryExecution(String functionName, Expression expr) {
			return (query) -> {
				try (ReindexerResultAccessor<?> it = toResultAccessor(query)) {
					return it.aggregationValue(functionName, COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(expr));
				}
			};
		}

		private Function<ReindexerQuery, Object> getDefaultQueryExecution() {
			return StringBasedReindexerQuery.super.getQueryExecution(StringBasedReindexerQuery.this.method);
		}

	}

	private final class ReindexerStatementVisitor extends StatementVisitorAdapter<Query<?>> {

		private final Lazy<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor;

		private final Lazy<SelectVisitor<Query<?>>> selectVisitor;

		private ReindexerStatementVisitor(ReindexerParameterAccessor parameterAccessor) {
			this.valueResolvingVisitor = StringBasedReindexerQuery.this.queryEvaluator
				.map((e) -> createValueResolvingVisitor(e, parameterAccessor));
			this.selectVisitor = Lazy
				.of(() -> new ReindexerSelectVisitor(parameterAccessor, this.valueResolvingVisitor));
		}

		@Override
		public <S> Query<?> visit(Select select, S ctx) {
			return select.accept(this.selectVisitor.get(), ctx);
		}

		@Override
		public <S> Query<?> visit(Update update, S ctx) {
			Query<?> criteria = createQuery(update.getTable().getName());
			for (UpdateSet updateSet : update.getUpdateSets()) {
				List<Column> columns = updateSet.getColumns();
				for (int i = 0; i < columns.size(); i++) {
					Column column = columns.get(i);
					Object value = StringBasedReindexerQuery.this.queryParameterMapper.mapParameterValue(
							column.getColumnName(),
							this.valueResolvingVisitor.get().resolveValue(updateSet.getValue(i)));
					criteria.set(column.getColumnName(), value);
				}
			}
			if (update.getWhere() != null) {
				ReindexerConditionalExpressionVisitor conditionalVisitor = new ReindexerConditionalExpressionVisitor(
						criteria, this.valueResolvingVisitor, this.selectVisitor.get());
				update.getWhere().accept(conditionalVisitor, new ConditionContext());
			}
			return criteria;
		}

		@Override
		public <S> Query<?> visit(Delete delete, S context) {
			Query<?> criteria = createQuery(delete.getTable().getName());
			if (delete.getWhere() != null) {
				ReindexerConditionalExpressionVisitor conditionalVisitor = new ReindexerConditionalExpressionVisitor(
						criteria, this.valueResolvingVisitor, this.selectVisitor.get());
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
		public <S> Query<?> visit(PlainSelect plainSelect, S context) {
			Table table = (Table) plainSelect.getFromItem();
			Query<?> root = createQuery(table.getName());
			// Apply select.
			List<String> fields = new ArrayList<>();
			for (SelectItem<?> selectItem : plainSelect.getSelectItems()) {
				Expression expr = selectItem.getExpression();
				if (expr instanceof AllColumns) {
					// Skip wildcard silently as it is added by default if select fields
					// are not specified.
					continue;
				}
				// Apply reqTotal if count function is in the query.
				if (FUNCTION_RESOLVING_VISITOR.resolveCount(expr) != null) {
					root.reqTotal();
					continue;
				}
				// Apply aggregateSum("field") if sum function is in the query.
				net.sf.jsqlparser.expression.Function sum = expr.accept(FUNCTION_RESOLVING_VISITOR, "sum");
				if (sum != null) {
					root.aggregateSum(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(sum));
					continue;
				}
				// Apply aggregateMin("field") if min function is in the query.
				net.sf.jsqlparser.expression.Function min = expr.accept(FUNCTION_RESOLVING_VISITOR, "min");
				if (min != null) {
					root.aggregateMin(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(min));
					continue;
				}
				// Apply aggregateMax("field") if max function is in the query.
				net.sf.jsqlparser.expression.Function max = expr.accept(FUNCTION_RESOLVING_VISITOR, "max");
				if (max != null) {
					root.aggregateMax(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(max));
					continue;
				}
				// Apply aggregateAvg("field") if avg function is in the query.
				net.sf.jsqlparser.expression.Function avg = expr.accept(FUNCTION_RESOLVING_VISITOR, "avg");
				if (avg != null) {
					root.aggregateAvg(COLUMN_RESOLVING_VISITOR.resolveRequiredIndexName(avg));
					continue;
				}
				// Apply aggregateFaced(fields...) if facet function is in the query.
				net.sf.jsqlparser.expression.Function facet = expr.accept(FUNCTION_RESOLVING_VISITOR, "facet");
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
					Query<?>.AggregationFacetRequest facetRequest = root
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
					continue;
				}
				// Add vectors() to fetch Vector fields.
				if (expr.accept(FUNCTION_RESOLVING_VISITOR, "vectors") != null) {
					fields.add("vectors()");
					continue;
				}
				// Apply withRank to include ranks in the result.
				if (expr.accept(FUNCTION_RESOLVING_VISITOR, "rank") != null) {
					root.withRank();
					continue;
				}
				Column column = COLUMN_RESOLVING_VISITOR.resolveColumn(expr);
				if (column != null) {
					fields.add(column.getColumnName());
					continue;
				}
				throw new InvalidDataAccessApiUsageException("Unexpected select item: " + selectItem);
			}
			root.select(fields.toArray(String[]::new));
			// Apply joins.
			if (plainSelect.getJoins() != null) {
				for (Join join : plainSelect.getJoins()) {
					Table joinTable = (Table) join.getFromItem();
					Query<?> joinQuery = createQuery(joinTable.getName());
					ReindexerJoinOnExpressionVisitor joinOnVisitor = new ReindexerJoinOnExpressionVisitor(joinQuery,
							this.valueResolvingVisitor, this);
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
						root, this.valueResolvingVisitor, this);
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

	private class ReindexerConditionalExpressionVisitor extends ExpressionVisitorAdapter<Query<?>> {

		final Query<?> criteria;

		private final Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor;

		private final SelectVisitor<Query<?>> selectVisitor;

		private ReindexerConditionalExpressionVisitor(Query<?> criteria,
				Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor,
				SelectVisitor<Query<?>> selectVisitor) {
			this.criteria = criteria;
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
			return switch (function.getName().toUpperCase()) {
				case "RANGE" -> {
					Assert.isTrue(function.getParameters() != null && function.getParameters().size() == 3,
							() -> "Expected exactly 3 parameters for: " + function);
					yield buildRangeCondition(function.getParameters().get(0), function.getParameters().get(1),
							function.getParameters().get(2), ctx);
				}
				case "KNN" -> {
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
			Object value = StringBasedReindexerQuery.this.queryParameterMapper.mapParameterValue(columnName,
					this.valueResolvingVisitor.get().resolveRequiredValue(expr));
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
						StringBasedReindexerQuery.this.queryParameterMapper.mapParameterValues(column.getColumnName(),
								start, end));
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

		private ReindexerJoinOnExpressionVisitor(Query<?> query,
				Supplier<ReindexerValueResolvingExpressionVisitor> valueResolvingVisitor,
				SelectVisitor<Query<?>> selectVisitor) {
			super(query, valueResolvingVisitor, selectVisitor);
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

	private static final class ReindexerFunctionResolvingExpressionVisitor
			extends ExpressionVisitorAdapter<net.sf.jsqlparser.expression.Function> {

		@Override
		public <S> net.sf.jsqlparser.expression.Function visit(net.sf.jsqlparser.expression.Function function,
				S context) {
			String functionName = (String) context;
			if (functionName.equalsIgnoreCase(function.getName())) {
				return function;
			}
			return null;
		}

		private net.sf.jsqlparser.expression.Function resolveCount(Expression expr) {
			net.sf.jsqlparser.expression.Function count = expr.accept(this, "count");
			if (count != null) {
				return count;
			}
			return expr.accept(this, "count_cached");
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
			Integer index = StringBasedReindexerQuery.this.namedParameters.get().get(name);
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
