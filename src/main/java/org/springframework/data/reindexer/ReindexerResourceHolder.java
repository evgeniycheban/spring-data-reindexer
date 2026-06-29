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
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.util.Assert;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.Transaction;

/**
 * Reindexer specific {@link ResourceHolderSupport} implementation.
 *
 * @author Evgeniy Cheban
 * @since 1.7
 */
public class ReindexerResourceHolder extends ResourceHolderSupport {

	private @Nullable Transaction<?> transaction;

	private final Reindexer reindexer;

	/**
	 * Creates an instance.
	 * @param reindexer the {@link Reindexer} to use
	 */
	public ReindexerResourceHolder(Reindexer reindexer) {
		Assert.notNull(reindexer, "reindexer cannot be null");
		this.reindexer = reindexer;
	}

	/**
	 * Returns an active transaction.
	 * @return the active {@link Transaction} or {@literal null} if none.
	 */
	public @Nullable Transaction<?> getActiveTransaction() {
		return this.transaction;
	}

	/**
	 * Begins a transaction if none is active.
	 */
	public void beginTransaction(String namespace, Class<?> domainType) {
		if (this.transaction == null) {
			this.transaction = this.reindexer.beginTransaction(namespace, domainType);
		}
	}

	/**
	 * Commits the active transaction.
	 */
	public void commitTransaction() {
		if (this.transaction != null) {
			this.transaction.commit();
		}
	}

	/**
	 * Rolls back the active transaction.
	 */
	public void rollbackTransaction() {
		if (this.transaction != null) {
			this.transaction.rollback();
		}
	}

}
