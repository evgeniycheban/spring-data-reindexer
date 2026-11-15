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

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.reindexer.core.mapping.event.AfterConvertCallback;
import org.springframework.data.reindexer.core.mapping.event.AfterSaveCallback;
import org.springframework.data.reindexer.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.reindexer.core.mapping.event.BeforeSaveCallback;
import org.springframework.data.reindexer.repository.item.TestItemEntityCallbacksRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItemEntityCallbacks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ReindexerRepository}'s
 * {@link org.springframework.data.mapping.callback.EntityCallbacks} support.
 *
 * @author Evgeniy Cheban
 */
class ReindexerEntityCallbacksRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemEntityCallbacksRepository repository;

	@Autowired
	BeforeConvertCallback<TestItemEntityCallbacks> beforeConvertCallback;

	@Autowired
	BeforeSaveCallback<TestItemEntityCallbacks> beforeSaveCallback;

	@Autowired
	AfterConvertCallback<TestItemEntityCallbacks> afterConvertCallback;

	@Autowired
	AfterSaveCallback<TestItemEntityCallbacks> afterSaveCallback;

	@Test
	void saveTriggersEntityCallbacks() {
		when(this.beforeConvertCallback.onBeforeConvert(any(), anyString()))
			.thenAnswer(invocation -> consumeEntityCallbackInvocation(invocation, (entity, namespace) -> entity
				.setBeforeConvert("onBeforeConvert_" + namespace + "_" + entity.getId())));
		when(this.beforeSaveCallback.onBeforeSave(any(), anyString()))
			.thenAnswer(invocation -> consumeEntityCallbackInvocation(invocation,
					(entity, namespace) -> entity.setBeforeSave("onBeforeSave_" + namespace + "_" + entity.getId())));
		when(this.afterConvertCallback.onAfterConvert(any(), anyString()))
			.thenAnswer(invocation -> consumeEntityCallbackInvocation(invocation, (entity, namespace) -> entity
				.setAfterConvert("onAfterConvert_" + namespace + "_" + entity.getId())));
		when(this.afterSaveCallback.onAfterSave(any(), anyString()))
			.thenAnswer(invocation -> consumeEntityCallbackInvocation(invocation,
					(entity, namespace) -> entity.setAfterSave("onAfterSave_" + namespace + "_" + entity.getId())));
		TestItemEntityCallbacks entity = this.repository.save(TestItemEntityCallbacks.builder().build());
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isNotNull();
		assertThat(entity.getBeforeConvert()).isEqualTo("onBeforeConvert_test_item_entity_callbacks_" + entity.getId());
		assertThat(entity.getBeforeSave()).isEqualTo("onBeforeSave_test_item_entity_callbacks_" + entity.getId());
		assertThat(entity.getAfterConvert()).isEqualTo("onAfterConvert_test_item_entity_callbacks_" + entity.getId());
		assertThat(entity.getAfterSave()).isEqualTo("onAfterSave_test_item_entity_callbacks_" + entity.getId());
		InOrder inOrder = inOrder(this.beforeConvertCallback, this.beforeSaveCallback, this.afterConvertCallback,
				this.afterSaveCallback);
		inOrder.verify(this.beforeConvertCallback).onBeforeConvert(entity, "test_item_entity_callbacks");
		inOrder.verify(this.beforeSaveCallback).onBeforeSave(entity, "test_item_entity_callbacks");
		inOrder.verify(this.afterConvertCallback).onAfterConvert(entity, "test_item_entity_callbacks");
		inOrder.verify(this.afterSaveCallback).onAfterSave(entity, "test_item_entity_callbacks");
		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void findByIdTriggersAfterConvertCallback() {
		TestItemEntityCallbacks entity = this.repository.save(TestItemEntityCallbacks.builder().build());
		TestItemEntityCallbacks found = this.repository.findById(entity.getId()).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isNotNull();
		assertThat(found.getAfterConvert()).isEqualTo("onAfterConvert_test_item_entity_callbacks_" + found.getId());
	}

	private TestItemEntityCallbacks consumeEntityCallbackInvocation(InvocationOnMock invocation,
			BiConsumer<TestItemEntityCallbacks, String> consumer) {
		TestItemEntityCallbacks entity = invocation.getArgument(0);
		String namespace = invocation.getArgument(1);
		consumer.accept(entity, namespace);
		return entity;
	}

}
