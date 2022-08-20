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

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.Transaction;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/**
 * A {@link Namespace} implementation that lookups for a {@link Transaction} and
 * delegates a call if it exists otherwise fallbacks to the original {@link Namespace}.
 *
 * @author Evgeniy Cheban
 * @since 1.1
 */
public class TransactionalNamespace<T> implements Namespace<T> {

	private final Namespace<T> fallback;

	/**
	 * Creates an instance.
	 *
	 * @param fallback the {@link Namespace} to use as a fallback
	 */
	public TransactionalNamespace(Namespace<T> fallback) {
		Assert.notNull(fallback, "fallback cannot be null");
		this.fallback = fallback;
	}

	@Override
	public Transaction<T> beginTransaction() {
		Transaction<T> tx = getTransaction();
		return (tx != null) ? tx : this.fallback.beginTransaction();
	}

	@Override
	public void insert(T item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.insert(item);
		}
		else {
			this.fallback.insert(item);
		}
	}

	@Override
	public void insert(String item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.insert(item);
		}
		else {
			this.fallback.insert(item);
		}
	}

	@Override
	public void upsert(T item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.upsert(item);
		}
		else {
			this.fallback.upsert(item);
		}
	}

	@Override
	public void upsert(String item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.upsert(item);
		}
		else {
			this.fallback.upsert(item);
		}
	}

	@Override
	public void update(T item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.update(item);
		}
		else {
			this.fallback.update(item);
		}
	}

	@Override
	public void update(String item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.update(item);
		}
		else {
			this.fallback.update(item);
		}
	}

	@Override
	public void delete(T item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.delete(item);
		}
		else {
			this.fallback.delete(item);
		}
	}

	@Override
	public void delete(String item) {
		Transaction<T> tx = getTransaction();
		if (tx != null) {
			tx.delete(item);
		}
		else {
			this.fallback.delete(item);
		}
	}

	@Override
	public Query<T> query() {
		Transaction<T> tx = getTransaction();
		return (tx != null) ? tx.query() : this.fallback.query();
	}

	@SuppressWarnings("unchecked")
	private Transaction<T> getTransaction() {
		return (Transaction<T>) TransactionSynchronizationManager.getResource(this.fallback);
	}

	@Override
	public void putMeta(String key, String data) {
		this.fallback.putMeta(key, data);
	}

	@Override
	public String getMeta(String key) {
		return this.fallback.getMeta(key);
	}

	@Override
	public ResultIterator<T> execSql(String query) {
		return this.fallback.execSql(query);
	}

	@Override
	public void updateSql(String query) {
		this.fallback.updateSql(query);
	}

}
