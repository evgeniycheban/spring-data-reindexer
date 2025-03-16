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
package org.springframework.data.reindexer.repository.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.reindexer.repository.util.PageableUtils;
import org.springframework.data.reindexer.repository.util.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.Assert;

/**
 * Repository base implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class SimpleReindexerRepository<T, ID> implements ReindexerRepository<T, ID> {

	private final ReindexerEntityInformation<T, ID> entityInformation;

	private final ReindexerMappingContext mappingContext;

	private final Reindexer reindexer;

	private final Namespace<T> namespace;

	private final ReindexerConverter reindexerConverter;

	/**
	 * Creates an instance.
	 *
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public SimpleReindexerRepository(ReindexerEntityInformation<T, ID> entityInformation,
			ReindexerMappingContext mappingContext, Reindexer reindexer, ReindexerConverter reindexerConverter) {
		this.entityInformation = entityInformation;
		this.mappingContext = mappingContext;
		this.reindexer = reindexer;
		this.reindexerConverter = reindexerConverter;
		this.namespace = openNamespace(entityInformation, reindexer);
	}

	private TransactionalNamespace<T> openNamespace(ReindexerEntityInformation<T, ID> entityInformation, Reindexer reindexer) {
		Namespace<T> namespace = reindexer.openNamespace(entityInformation.getNamespaceName(),
				entityInformation.getNamespaceOptions(), entityInformation.getJavaType());
		return new TransactionalNamespace<>(namespace);
	}

	@Override
	public <S extends T> S save(S entity) {
		Assert.notNull(entity, "Entity must not be null!");
		if (this.entityInformation.isNew(entity)) {
			this.namespace.insert(entity);
		}
		else {
			this.namespace.upsert(entity);
		}
		return entity;
	}

	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities) {
		Assert.notNull(entities, "The given Iterable of entities must not be null!");
		List<S> result = new ArrayList<>();
		for (S entity : entities) {
			result.add(save(entity));
		}
		return result;
	}

	@Override
	public Optional<T> findById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		return joinedQuery().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).findOne()
				.map(e -> this.reindexerConverter.read(this.entityInformation.getJavaType(), e));
	}

	@Override
	public boolean existsById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		return query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).exists();
	}

	@Override
	public List<T> findAll() {
		return joinedQuery().stream()
				.map(e -> this.reindexerConverter.read(this.entityInformation.getJavaType(), e))
				.collect(Collectors.toList());
	}

	@Override
	public List<T> findAll(Sort sort) {
		Query<T> query = joinedQuery();
		for (Order order : sort) {
			query = query.sort(order.getProperty(), order.isDescending());
		}
		return query.stream()
				.map(e -> this.reindexerConverter.read(this.entityInformation.getJavaType(), e))
				.collect(Collectors.toList());
	}

	@Override
	public Page<T> findAll(Pageable pageable) {
		if (pageable.isUnpaged()) {
			return new PageImpl<>(findAll());
		}
		Query<T> query = joinedQuery();
		for (Order order : pageable.getSort()) {
			query.sort(order.getProperty(), order.isDescending());
		}
		query.limit(pageable.getPageSize()).offset(PageableUtils.getOffsetAsInteger(pageable)).reqTotal();
		try (ResultIterator<T> iterator = query.execute()) {
			List<T> content = new ArrayList<>();
			while (iterator.hasNext()) {
				content.add(this.reindexerConverter.read(this.entityInformation.getJavaType(), iterator.next()));
			}
			return PageableExecutionUtils.getPage(content, pageable, iterator::getTotalCount);
		}
	}

	@Override
	public List<T> findAllById(Iterable<ID> ids) {
		Assert.notNull(ids, "The given Ids of entities not be null!");
		return joinedQuery().where(this.entityInformation.getIdFieldName(), Query.Condition.SET, toSet(ids)).stream()
				.map(e -> this.reindexerConverter.read(this.entityInformation.getJavaType(), e))
				.collect(Collectors.toList());
	}

	private Set<ID> toSet(Iterable<? extends ID> ids) {
		Set<ID> result = new HashSet<>();
		ids.forEach(result::add);
		return result;
	}

	@Override
	public Query<T> query() {
		return this.namespace.query();
	}

	@Override
	public long count() {
		return query().count();
	}

	@Override
	public void deleteById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).delete();
	}

	@Override
	public void delete(T entity) {
		Assert.notNull(entity, "The given entity must not be null!");
		this.namespace.delete(entity);
	}

	@Override
	public void deleteAllById(Iterable<? extends ID> ids) {
		Assert.notNull(ids, "The given Iterable of ids must not be null!");
		query().where(this.entityInformation.getIdFieldName(), Query.Condition.SET, toSet(ids)).delete();
	}

	@Override
	public void deleteAll(Iterable<? extends T> entities) {
		Assert.notNull(entities, "The given Iterable of entities must not be null!");
		entities.forEach(this::delete);
	}

	@Override
	public void deleteAll() {
		query().delete();
	}

	@SuppressWarnings("unchecked")
	private Query<T> joinedQuery() {
		Query<T> query = query();
		return (Query<T>) QueryUtils.withJoins(query, this.entityInformation.getJavaType(), this.mappingContext, this.reindexer);
	}

}
