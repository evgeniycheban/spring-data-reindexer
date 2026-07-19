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
package org.springframework.data.reindexer.repository.item.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
@Transactional(transactionManager = "txManager")
@Service
public class TestItemTransactionalService {

	private final TestItemTransactionalService self;

	private final TestItemReindexerRepository repository;

	public TestItemTransactionalService(@Lazy TestItemTransactionalService self,
			TestItemReindexerRepository repository) {
		this.self = self;
		this.repository = repository;
	}

	public TestItem save(TestItem item) {
		return this.repository.save(item);
	}

	@Transactional(transactionManager = "txManager", propagation = Propagation.REQUIRES_NEW)
	public void saveRequiresNew(TestItem item) {
		this.repository.save(item);
	}

	public void saveAndDelete(TestItem testItem) {
		this.repository.save(testItem);
		this.repository.delete(testItem);
	}

	public void saveExceptionally(TestItem item) {
		this.repository.save(item);
		throw new IllegalStateException();
	}

	public void saveExceptionallyDelegatesToRequiresNew(TestItem item) {
		this.self.saveRequiresNew(item);
		throw new IllegalStateException();
	}

	public void saveExceptionallyDelegatesToExisting(TestItem item) {
		this.self.save(item);
		throw new IllegalStateException();
	}

}
