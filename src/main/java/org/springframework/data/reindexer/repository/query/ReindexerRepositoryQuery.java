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

import java.util.Collection;
import java.util.Iterator;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.repository.query.parser.PartTree.OrPart;
import org.springframework.util.Assert;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryQuery implements RepositoryQuery {

	private final ReindexerQueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final PartTree tree;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link ReindexerQueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use                         
	 */
	public ReindexerRepositoryQuery(ReindexerQueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		this.namespace = reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.tree = new PartTree(queryMethod.getName(), entityInformation.getJavaType());
	}

	@Override
	public Object execute(Object[] parameters) {
		Query<?> query = createQuery(parameters);
		if (this.queryMethod.isCollectionQuery()) {
			return query.toList();
		}
		if (this.queryMethod.isStreamQuery()) {
			return query.stream();
		}
		if (this.queryMethod.isIteratorQuery()) {
			return query.execute();
		}
		if (this.queryMethod.isQueryForEntity()) {
			return query.getOne();
		}
		return query.findOne();
	}

	private Query<?> createQuery(Object[] parameters) {
		ParametersParameterAccessor accessor =
				new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		Query<?> base = null;
		Iterator<Object> iterator = accessor.iterator();
		for (OrPart node : this.tree) {
			Iterator<Part> parts = node.iterator();
			Assert.state(parts.hasNext(), () -> "No part found in PartTree " + this.tree);
			Query<?> criteria = where(parts.next(), (base != null) ? base : this.namespace.query(), iterator);
			while (parts.hasNext()) {
				criteria = where(parts.next(), criteria, iterator);
			}
			base = criteria.or();
		}
		return base;
	}

	private Query<?> where(Part part, Query<?> criteria, Iterator<Object> parameters) {
		String indexName = part.getProperty().toDotPath();
		switch (part.getType()) {
			case GREATER_THAN:
				return criteria.where(indexName, Query.Condition.GT, parameters.next());
			case GREATER_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.GE, parameters.next());
			case LESS_THAN:
				return criteria.where(indexName, Query.Condition.LT, parameters.next());
			case LESS_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.LE, parameters.next());
			case IN:
			case CONTAINING:
				return createInQuery(criteria, indexName, parameters);
			case NOT_IN:
			case NOT_CONTAINING:
				return createInQuery(criteria.not(), indexName, parameters);
			case IS_NOT_NULL:
				return criteria.isNotNull(indexName);
			case IS_NULL:
				return criteria.isNull(indexName);
			case SIMPLE_PROPERTY:
				return criteria.where(indexName, Query.Condition.EQ, parameters.next());
			case NEGATING_SIMPLE_PROPERTY:
				return criteria.not().where(indexName, Query.Condition.EQ, parameters.next());
			default:
				throw new IllegalArgumentException("Unsupported keyword!");
		}
	}

    private Query<?> createInQuery(Query<?> criteria, String indexName, Iterator<Object> parameters) {
        Object value = parameters.next();
        Assert.isInstanceOf(Collection.class, value, () -> "Expected Collection but got " + value);
        return criteria.where(indexName, Query.Condition.SET, (Collection<?>) value);
    }

	@Override
	public ReindexerQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

}
