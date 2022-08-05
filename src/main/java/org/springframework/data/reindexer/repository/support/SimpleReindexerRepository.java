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

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.util.Assert;

/**
 * Repository base implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class SimpleReindexerRepository<T, ID> implements ReindexerRepository<T, ID> {

	private final ReindexerEntityInformation<T, ID> entityInformation;

	private final Namespace<T> namespace;

	/**
	 * Creates an instance.
	 *
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use
	 */
	public SimpleReindexerRepository(ReindexerEntityInformation<T, ID> entityInformation, Reindexer reindexer) {
		this.entityInformation = entityInformation;
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
		return this.namespace.query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).findOne();
	}

	@Override
	public boolean existsById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		return this.namespace.query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).exists();
	}

	@Override
	public List<T> findAll() {
		return this.namespace.query().toList();
	}

	@Override
	public List<T> findAllById(Iterable<ID> ids) {
		Assert.notNull(ids, "The given Ids of entities not be null!");
		return this.namespace.query().where(this.entityInformation.getIdFieldName(), Query.Condition.SET, toSet(ids)).toList();
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
		return this.namespace.query().count();
	}

	@Override
	public void deleteById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		this.namespace.query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).delete();
	}

	@Override
	public void delete(T entity) {
		Assert.notNull(entity, "The given entity must not be null!");
		this.namespace.delete(entity);
	}

	@Override
	public void deleteAllById(Iterable<? extends ID> ids) {
		Assert.notNull(ids, "The given Iterable of ids must not be null!");
		this.namespace.query().where(this.entityInformation.getIdFieldName(), Query.Condition.SET, toSet(ids)).delete();
	}

	@Override
	public void deleteAll(Iterable<? extends T> entities) {
		Assert.notNull(entities, "The given Iterable of entities must not be null!");
		entities.forEach(this::delete);
	}

	@Override
	public void deleteAll() {
		this.namespace.query().delete();
	}

}
