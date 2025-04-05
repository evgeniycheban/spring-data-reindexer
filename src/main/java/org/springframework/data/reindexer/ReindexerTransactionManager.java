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
package org.springframework.data.reindexer;

import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.Transaction;

import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/**
 * A {@link org.springframework.transaction.PlatformTransactionManager} that manages
 * {@link Transaction}s for a single {@link ru.rt.restream.reindexer.Namespace}.
 *
 * @see org.springframework.transaction.annotation.Transactional
 * @author Evgeniy Cheban
 * @since 1.1
 */
public class ReindexerTransactionManager<T> extends AbstractPlatformTransactionManager {

	private final Reindexer reindexer;

	private final ReindexerMappingContext mappingContext;

	private final ReindexerNamespace<T> namespace;

	/**
	 * Creates an instance.
	 * @param reindexer the {@link Reindexer} instance to use
	 * @param domainClass the domain class to use
	 */
	public ReindexerTransactionManager(Reindexer reindexer, ReindexerMappingContext mappingContext,
			Class<T> domainClass) {
		Assert.notNull(reindexer, "reindexer cannot be null");
		Assert.notNull(mappingContext, "mappingContext cannot be null");
		Assert.notNull(domainClass, "domainClass cannot be null");
		this.reindexer = reindexer;
		this.mappingContext = mappingContext;
		this.namespace = openNamespace(domainClass);
	}

	@SuppressWarnings("unchecked")
	private ReindexerNamespace<T> openNamespace(Class<T> domainClass) {
		ReindexerPersistentEntity<?> persistentEntity = this.mappingContext.getRequiredPersistentEntity(domainClass);
		return (ReindexerNamespace<T>) this.reindexer.openNamespace(persistentEntity.getNamespace(),
				persistentEntity.getNamespaceOptions(), persistentEntity.getType());
	}

	@Override
	protected Transaction<T> doGetTransaction() throws TransactionException {
		Transaction<T> transaction = new Transaction<>(this.namespace, this.reindexer);
		TransactionSynchronizationManager.bindResource(this.namespace, transaction);
		return transaction;
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
		extractReindexerTransaction(transaction).start();
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
		extractReindexerTransaction(status.getTransaction()).commit();
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
		extractReindexerTransaction(status.getTransaction()).rollback();
	}

	@SuppressWarnings("unchecked")
	private Transaction<T> extractReindexerTransaction(Object transaction) {
		Assert.isInstanceOf(Transaction.class, transaction, () -> String
			.format("Expected to find a %s but it turned out to be %s.", Transaction.class, transaction.getClass()));
		return (Transaction<T>) transaction;
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		TransactionSynchronizationManager.unbindResource(this.namespace);
	}

}
