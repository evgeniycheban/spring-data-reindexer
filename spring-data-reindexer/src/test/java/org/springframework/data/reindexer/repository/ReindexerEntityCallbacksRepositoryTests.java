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
import org.springframework.data.reindexer.core.convert.LazyLoadingProxy;
import org.springframework.data.reindexer.repository.item.TestItemEntityCallbacksRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItemEntityCallbacks;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReindexerRepository}'s
 * {@link org.springframework.data.mapping.callback.EntityCallbacks} support.
 *
 * @author Evgeniy Cheban
 */
class ReindexerEntityCallbacksRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemEntityCallbacksRepository repository;

	@Test
	void saveInitializesLazyPropertiesWithProxies() {
		TestItemEntityCallbacks parent = this.repository.save(TestItemEntityCallbacks.builder().build());
		assertThat(parent).isNotNull();
		assertThat(parent.getId()).isNotNull();
		assertThat(parent.getParent()).isNull();
		TestItemEntityCallbacks child = this.repository
			.save(TestItemEntityCallbacks.builder().parentId(parent.getId()).build());
		assertThat(child).isNotNull();
		assertThat(child.getId()).isNotNull();
		assertThat(child.getParent()).isInstanceOf(LazyLoadingProxy.class);
		assertThat(child.getParent().getId()).isEqualTo(parent.getId());
		assertThat(child.getParent().getParent()).isNull();
	}

	@Test
	void saveTriggersBeforeConvertCallback() {
		TestItemEntityCallbacks entity = this.repository.save(TestItemEntityCallbacks.builder().build());
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isNotNull();
		assertThat(entity.getBeforeConvert()).isEqualTo("onBeforeConvert_test_item_entity_callbacks_" + entity.getId());
		assertThat(entity.getParent()).isNull();
	}

}
