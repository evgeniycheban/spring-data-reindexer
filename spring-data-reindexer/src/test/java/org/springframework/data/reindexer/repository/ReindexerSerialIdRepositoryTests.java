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
import org.springframework.data.reindexer.repository.item.TestItemWithSerialIdRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItemWithSerialId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Evgeniy Cheban
 */
class ReindexerSerialIdRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemWithSerialIdRepository repository;

	@Test
	void saveWhenIdIsNullThenGeneratesIdUsingSerialIdGenerator() {
		TestItemWithSerialId item = new TestItemWithSerialId(null, "TestName");
		TestItemWithSerialId saved = this.repository.save(item);
		assertThat(saved).isNotNull();
		assertThat(saved).isSameAs(item);
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getName()).isEqualTo("TestName");
	}

	@Test
	void saveWhenIdIsNotNullThenUpsertsItemUsingId() {
		TestItemWithSerialId item = new TestItemWithSerialId(null, "TestName");
		TestItemWithSerialId saved = this.repository.save(item);
		assertThat(saved).isNotNull();
		Long id = saved.getId();
		assertThat(id).isNotNull();
		assertThat(saved.getName()).isEqualTo("TestName");
		saved.setName("TestNameUpdated");
		TestItemWithSerialId updated = this.repository.save(saved);
		assertThat(updated).isNotNull();
		assertThat(updated).isSameAs(saved);
		assertThat(updated.getId()).isEqualTo(id);
		assertThat(updated.getName()).isEqualTo("TestNameUpdated");
	}

}
