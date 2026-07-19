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
package org.springframework.data.reindexer.repository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.item.service.TestItemTransactionalService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ReindexerRepository}'s transactional methods.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
class ReindexerTransactionalRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Autowired
	TestItemTransactionalService service;

	@Test
	void saveTransactional() {
		TestItem testItem = this.service.save(new TestItem(1L, "TestName", "TestValue"));
		assertThat(testItem).isNotNull();
		TestItem item = this.repository.findById(1L).orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(testItem.getId());
		assertThat(item.getName()).isEqualTo(testItem.getName());
		assertThat(item.getValue()).isEqualTo(testItem.getValue());
	}

	@Test
	void saveAndDelete() {
		TestItem testItem = new TestItem(1L, "TestName", "TestValue");
		this.service.saveAndDelete(testItem);
		assertFalse(this.repository.existsById(testItem.getId()));
	}

	@Test
	void saveTransactionalExceptionally() {
		assertThrows(IllegalStateException.class,
				() -> this.service.saveExceptionally(new TestItem(1L, "TestName", "TestValue")));
		assertFalse(this.repository.existsById(1L));
	}

	@Test
	void saveExceptionallyDelegatesToRequiresNew() {
		assertThatIllegalStateException().isThrownBy(
				() -> this.service.saveExceptionallyDelegatesToRequiresNew(new TestItem(1L, "TestName", "TestValue")));
		assertThat(this.repository.existsById(1L)).isTrue();
	}

	@Test
	void saveExceptionallyDelegatesToExisting() {
		assertThatIllegalStateException().isThrownBy(
				() -> this.service.saveExceptionallyDelegatesToExisting(new TestItem(1L, "TestName", "TestValue")));
		assertThat(this.repository.existsById(1L)).isFalse();
	}

}
