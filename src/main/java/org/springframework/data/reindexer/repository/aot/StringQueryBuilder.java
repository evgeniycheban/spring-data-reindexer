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
package org.springframework.data.reindexer.repository.aot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import ru.rt.restream.reindexer.Query;

import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.util.StringUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class StringQueryBuilder {

	private QueryType type = QueryType.SELECT;

	private String namespace;

	private boolean reqTotal;

	private Integer limit;

	private final Deque<QueryEntry> whereStack = new ArrayDeque<>();

	private final List<QueryEntry> whereEntries = new ArrayList<>();

	private final List<JoinEntry> joinEntries = new ArrayList<>();

	private final List<QueryEntry> onEntries = new ArrayList<>();

	private final List<SortEntry> sortEntries = new ArrayList<>();

	private final List<String> selectFields = new ArrayList<>();

	private final List<AggregateEntry> aggregateEntries = new ArrayList<>();

	void type(QueryType type) {
		this.type = type;
	}

	void select(String field) {
		this.selectFields.add(field);
	}

	void namespace(String namespace) {
		this.namespace = namespace;
	}

	void reqTotal() {
		this.reqTotal = true;
	}

	void limit(int limit) {
		this.limit = limit;
	}

	void join(StringQueryBuilder joinStringQueryBuilder, JoinType joinType) {
		JoinEntry joinEntry = new JoinEntry();
		joinEntry.type = joinType;
		joinEntry.joinStringQueryBuilder = joinStringQueryBuilder;
		this.joinEntries.add(joinEntry);
		if (joinEntry.type != JoinType.LEFT) {
			QueryEntry queryEntry = new QueryEntry();
			queryEntry.joinIndex = joinEntries.size() - 1;
			queryEntry.operation = Operation.AND;
			if (!this.whereStack.isEmpty()) {
				QueryEntry parent = this.whereStack.getLast();
				parent.children.add(queryEntry);
			}
			else {
				this.whereEntries.add(queryEntry);
			}
		}
	}

	void on(Operation operation, String joinField, Query.Condition condition, String joinIndex) {
		QueryEntry queryEntry = new QueryEntry();
		queryEntry.operation = operation;
		queryEntry.field = joinField;
		queryEntry.condition = condition;
		queryEntry.parameterNames.add(joinIndex);
		this.onEntries.add(queryEntry);
	}

	void where(Operation operation, String field, Query.Condition condition, boolean negated,
			String... parameterNames) {
		QueryEntry queryEntry = new QueryEntry();
		queryEntry.operation = operation;
		queryEntry.field = field;
		queryEntry.condition = condition;
		queryEntry.negated = negated;
		queryEntry.parameterNames.addAll(Arrays.asList(parameterNames));
		if (!this.whereStack.isEmpty()) {
			QueryEntry parent = this.whereStack.getLast();
			parent.children.add(queryEntry);
		}
		else {
			this.whereEntries.add(queryEntry);
		}
	}

	void aggregate(AggregateType type, String... fields) {
		AggregateEntry aggregateEntry = new AggregateEntry();
		aggregateEntry.type = type;
		aggregateEntry.fields.addAll(Arrays.asList(fields));
		this.aggregateEntries.add(aggregateEntry);
	}

	void sort(String sortIndex, boolean desc, Object... values) {
		SortEntry sortEntry = new SortEntry();
		sortEntry.sortIndex = sortIndex;
		sortEntry.desc = desc;
		sortEntry.values.addAll(Arrays.asList(values));
		this.sortEntries.add(sortEntry);
	}

	void openBracket(Operation operation) {
		QueryEntry queryEntry = new QueryEntry();
		queryEntry.operation = operation;
		if (!this.whereStack.isEmpty()) {
			QueryEntry parent = this.whereStack.getLast();
			parent.children.add(queryEntry);
		}
		else {
			this.whereEntries.add(queryEntry);
		}
		this.whereStack.add(queryEntry);
	}

	void closeBracket() {
		this.whereStack.pollLast();
	}

	String getSql() {
		StringBuilder stringBuilder = new StringBuilder(this.type.name());
		if (this.type == QueryType.SELECT) {
			stringBuilder.append(" ").append(getSelectPart());
			if (this.reqTotal) {
				stringBuilder.append(", COUNT(*)");
			}
		}
		stringBuilder.append(" FROM");
		stringBuilder.append(" ").append(this.namespace);
		if (!this.joinEntries.isEmpty()) {
			stringBuilder.append(getJoinPart());
		}
		if (!this.whereEntries.isEmpty()) {
			stringBuilder.append(" WHERE ").append(getWherePart(this.whereEntries));
		}
		if (!this.sortEntries.isEmpty()) {
			stringBuilder.append(getOrderByPart(this.sortEntries));
		}
		if (this.limit != null) {
			stringBuilder.append(" LIMIT").append(" ").append(this.limit);
		}
		return stringBuilder.toString();
	}

	private String getSelectPart() {
		if (!this.aggregateEntries.isEmpty()) {
			return this.aggregateEntries.stream().map(this::getAggregationValue).collect(Collectors.joining(", "));
		}
		if (!this.selectFields.isEmpty()) {
			return String.join(", ", this.selectFields);
		}
		return "*";
	}

	private String getAggregationValue(AggregateEntry aggregateEntry) {
		return aggregateEntry.type.name() + "(" + String.join(", ", aggregateEntry.fields) + ")";
	}

	private String getJoinPart() {
		String joinPart = this.joinEntries.stream()
			.filter(joinEntry -> joinEntry.type == JoinType.LEFT)
			.map(this::getSingleJoinPart)
			.collect(Collectors.joining(" "));
		return StringUtils.hasText(joinPart) ? " " + joinPart : "";
	}

	private String getSingleJoinPart(JoinEntry joinEntry) {
		StringQueryBuilder joinStringQueryBuilder = joinEntry.joinStringQueryBuilder;
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(getJoinTypePart(joinEntry.type))
			.append(" ")
			.append(joinStringQueryBuilder.whereEntries.isEmpty() ? joinStringQueryBuilder.namespace
					: "(" + joinStringQueryBuilder.getSql() + ")")
			.append(" ON ");
		if (joinStringQueryBuilder.onEntries.size() > 1) {
			stringBuilder.append("(");
		}
		for (QueryEntry onEntry : joinStringQueryBuilder.onEntries) {
			if (onEntry != joinStringQueryBuilder.onEntries.get(0)) {
				stringBuilder.append(" ").append(onEntry.operation).append(" ");
			}
			stringBuilder.append(joinStringQueryBuilder.namespace)
				.append(".")
				.append(onEntry.parameterNames.get(0))
				.append(" ")
				.append(getConditionPart(onEntry.condition))
				.append(" ")
				.append(this.namespace)
				.append(".")
				.append(onEntry.field);
		}
		if (joinStringQueryBuilder.onEntries.size() > 1) {
			stringBuilder.append(")");
		}
		return stringBuilder.toString();
	}

	private String getJoinTypePart(JoinType joinType) {
		return switch (joinType) {
			case LEFT -> "LEFT JOIN";
			case INNER -> "INNER JOIN";
		};
	}

	private String getWherePart(List<QueryEntry> whereEntries) {
		StringBuilder stringBuilder = new StringBuilder();
		for (QueryEntry whereEntry : whereEntries) {
			if (whereEntry.joinIndex != -1) {
				JoinEntry joinEntry = joinEntries.get(whereEntry.joinIndex);
				if (whereEntry != whereEntries.get(0)) {
					stringBuilder.append(" ").append(whereEntry.operation).append(" ");
				}
				stringBuilder.append(getSingleJoinPart(joinEntry));
			}
			else if (!whereEntry.children.isEmpty()) {
				if (whereEntry != whereEntries.get(0)) {
					stringBuilder.append(" ").append(whereEntry.operation).append(" ");
				}
				stringBuilder.append("(").append(getWherePart(whereEntry.children)).append(")");
			}
			else {
				if (whereEntry != whereEntries.get(0)) {
					stringBuilder.append(" ").append(whereEntry.operation).append(" ");
				}
				if (whereEntry.negated) {
					stringBuilder.append("NOT").append(" ");
				}
				stringBuilder.append(whereEntry.field).append(" ");
				stringBuilder.append(getConditionPart(whereEntry.condition));
				if (whereEntry.parameterNames.size() == 1) {
					Object parameterName = whereEntry.parameterNames.get(0);
					stringBuilder.append(" ").append(parameterName);
				}
				else if (whereEntry.parameterNames.size() > 1) {
					stringBuilder.append(" (");
					String parameterNames = String.join(", ", whereEntry.parameterNames);
					stringBuilder.append(parameterNames).append(")");
				}
			}
		}
		return stringBuilder.toString();
	}

	private String getConditionPart(Query.Condition condition) {
		return switch (condition) {
			case ANY -> "IS NOT NULL";
			case EQ -> "=";
			case LT -> "<";
			case LE -> "<=";
			case GT -> ">";
			case GE -> ">=";
			case RANGE -> "RANGE";
			case SET -> "IN";
			case ALLSET -> "ALLSET";
			case EMPTY -> "IS NULL";
			case LIKE -> "LIKE";
		};
	}

	private String getOrderByPart(List<SortEntry> sortEntries) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(" ORDER BY ");
		for (SortEntry sortEntry : sortEntries) {
			if (sortEntry != sortEntries.get(0)) {
				stringBuilder.append(", ");
			}
			if (!sortEntry.values.isEmpty()) {
				String forcedOrderValues = sortEntry.values.stream()
					.map(String::valueOf)
					.map(this::addQuotes)
					.collect(Collectors.joining(", "));
				stringBuilder.append("FIELD(")
					.append(sortEntry.sortIndex)
					.append(", ")
					.append(forcedOrderValues)
					.append(")");
			}
			else {
				stringBuilder.append(addQuotes(sortEntry.sortIndex));
			}

			if (sortEntry.desc) {
				stringBuilder.append(" DESC");
			}
		}
		return stringBuilder.toString();
	}

	private String addQuotes(Object value) {
		return "'" + value + "'";
	}

	enum QueryType {

		SELECT, DELETE

	}

	enum Operation {

		OR, AND

	}

	enum AggregateType {

		MIN, MAX, SUM, AVG, FACET, DISTINCT

	}

	private static class AggregateEntry {

		private AggregateType type;

		private final List<String> fields = new ArrayList<>();

	}

	private static class QueryEntry {

		private Operation operation;

		private String field;

		private Query.Condition condition;

		private boolean negated;

		private int joinIndex = -1;

		private final List<String> parameterNames = new ArrayList<>();

		private final List<QueryEntry> children = new ArrayList<>();

	}

	private static class JoinEntry {

		private StringQueryBuilder joinStringQueryBuilder;

		private JoinType type;

	}

	private static class SortEntry {

		private String sortIndex;

		private boolean desc;

		private final List<Object> values = new ArrayList<>();

	}

}
