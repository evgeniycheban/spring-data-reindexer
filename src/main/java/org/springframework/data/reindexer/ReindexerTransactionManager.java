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
package org.springframework.data.reindexer;

import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.Transaction;

import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
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
	protected Object doGetTransaction() throws TransactionException {
		ReindexerResourceHolder resourceHolder = (ReindexerResourceHolder) TransactionSynchronizationManager
			.getResource(this.namespace);
		return new ReindexerTransactionObject(resourceHolder, this.namespace);
	}

	@Override
	protected boolean isExistingTransaction(Object transaction) throws TransactionException {
		return extractReindexerTransaction(transaction).hasResourceHolder();
	}

	@Override
	protected Object doSuspend(Object transaction) throws TransactionException {
		return TransactionSynchronizationManager.unbindResource(this.namespace);
	}

	@Override
	protected void doResume(Object transaction, Object suspendedResources) throws TransactionException {
		TransactionSynchronizationManager.bindResource(this.namespace, suspendedResources);
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
		ReindexerTransactionObject transactionObject = extractReindexerTransaction(transaction);
		ReindexerResourceHolder resourceHolder = new ReindexerResourceHolder(this.reindexer);
		transactionObject.setResourceHolder(resourceHolder);
		if (logger.isDebugEnabled()) {
			logger.debug("About to start transaction for namespace: %s".formatted(this.namespace.getName()));
		}
		try {
			transactionObject.beginTransaction();
		}
		catch (Exception ex) {
			throw new TransactionSystemException(
					"Could not start transaction for namespace: %s".formatted(this.namespace.getName()), ex);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Started transaction for namespace: %s".formatted(this.namespace.getName()));
		}
		TransactionSynchronizationManager.bindResource(this.namespace, resourceHolder);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
		ReindexerTransactionObject transactionObject = extractReindexerTransaction(status.getTransaction());
		if (logger.isDebugEnabled()) {
			logger.debug("About to commit transaction for namespace: %s".formatted(this.namespace.getName()));
		}
		try {
			transactionObject.commitTransaction();
		}
		catch (Exception ex) {
			throw new TransactionSystemException(
					"Could not commit transaction for namespace: %s".formatted(this.namespace.getName()), ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
		ReindexerTransactionObject transactionObject = extractReindexerTransaction(status.getTransaction());
		if (logger.isDebugEnabled()) {
			logger.debug("About to rollback transaction for namespace: %s".formatted(this.namespace.getName()));
		}
		try {
			transactionObject.rollbackTransaction();
		}
		catch (Exception ex) {
			throw new TransactionSystemException(
					"Could not rollback transaction for namespace: %s".formatted(this.namespace.getName()), ex);
		}
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		TransactionSynchronizationManager.unbindResource(this.namespace);
	}

	private ReindexerTransactionObject extractReindexerTransaction(Object transaction) {
		Assert.isInstanceOf(ReindexerTransactionObject.class, transaction,
				() -> String.format("Expected to find a %s but it turned out to be %s.",
						ReindexerTransactionObject.class, transaction.getClass()));
		return (ReindexerTransactionObject) transaction;
	}

	/**
	 * Reindexer specific transaction object, representing a
	 * {@link ReindexerResourceHolder}. Used as transaction by
	 * {@link ReindexerTransactionManager}.
	 *
	 * @author Evgeniy Cheban
	 * @since 1.7
	 */
	protected static class ReindexerTransactionObject implements SmartTransactionObject {

		private @Nullable ReindexerResourceHolder resourceHolder;

		private final ReindexerNamespace<?> namespace;

		/**
		 * Creates an instance.
		 * @param resourceHolder the {@link ReindexerResourceHolder} to use
		 * @param namespace the {@link ReindexerNamespace} to use
		 */
		ReindexerTransactionObject(@Nullable ReindexerResourceHolder resourceHolder, ReindexerNamespace<?> namespace) {
			this.resourceHolder = resourceHolder;
			this.namespace = namespace;
		}

		/**
		 * Sets a {@link ReindexerResourceHolder}.
		 * @param resourceHolder the {@link ReindexerResourceHolder} to use
		 */
		void setResourceHolder(@Nullable ReindexerResourceHolder resourceHolder) {
			this.resourceHolder = resourceHolder;
		}

		/**
		 * Returns {@literal true} if a {@link ReindexerResourceHolder} is set.
		 * @return {@literal true} if a {@link ReindexerResourceHolder} is set
		 */
		final boolean hasResourceHolder() {
			return this.resourceHolder != null;
		}

		/**
		 * Begins a transaction if a {@link ReindexerResourceHolder} is set.
		 */
		void beginTransaction() {
			if (hasResourceHolder()) {
				this.resourceHolder.beginTransaction(this.namespace.getName(), this.namespace.getItemClass());
			}
		}

		/**
		 * Commits the transaction if a {@link ReindexerResourceHolder} is set.
		 */
		public void commitTransaction() {
			if (hasResourceHolder()) {
				this.resourceHolder.commitTransaction();
			}
		}

		/**
		 * Rolls back the transaction if a {@link ReindexerResourceHolder} is set.
		 */
		public void rollbackTransaction() {
			if (hasResourceHolder()) {
				this.resourceHolder.rollbackTransaction();
			}
		}

	}

}
