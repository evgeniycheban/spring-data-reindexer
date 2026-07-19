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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.reindexer.LazyLoadingException;
import org.springframework.data.reindexer.container.ReindexerTestContainer;
import org.springframework.data.reindexer.repository.item.TestItemContainerRepository;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.TestJoinedItemRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.item.entity.TestItemContainer;
import org.springframework.data.reindexer.repository.item.entity.TestJoinedItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link ReindexerRepository}'s namespace joins.
 *
 * @author Evgeniy Cheban
 */
class ReindexerJoinRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Autowired
	TestJoinedItemRepository joinedItemRepository;

	@Autowired
	TestItemContainerRepository itemContainerRepository;

	@Test
	void findByNameWithJoinedItems() {
		TestJoinedItem nestedJoinedItem = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName1"));
		TestJoinedItem joinedItem = this.joinedItemRepository
			.save(new TestJoinedItem(2L, nestedJoinedItem.getId(), "TestName2"));
		Map<Long, TestJoinedItem> expectedJoinedItems = new HashMap<>();
		expectedJoinedItems.put(3L,
				this.joinedItemRepository.save(new TestJoinedItem(3L, nestedJoinedItem.getId(), "TestName3")));
		expectedJoinedItems.put(4L,
				this.joinedItemRepository.save(new TestJoinedItem(4L, nestedJoinedItem.getId(), "TestName4")));
		expectedJoinedItems.put(5L,
				this.joinedItemRepository.save(new TestJoinedItem(5L, nestedJoinedItem.getId(), "TestName5")));
		List<Long> joinedItemIds = new ArrayList<>(expectedJoinedItems.keySet());
		TestItem expectedItem = this.repository
			.save(new TestItem(1L, null, joinedItem.getId(), joinedItemIds, "TestName", "TestValue", null, null));
		TestItem foundItem = this.repository.findByName("TestName").orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getNestedItem()).isNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getJoinedItem().getId()).isEqualTo(joinedItem.getId());
		assertThat(foundItem.getJoinedItem().getName()).isEqualTo(joinedItem.getName());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem()).isNotNull();
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
		assertThat(foundItem.getJoinedItems()).hasSize(expectedJoinedItems.size());
		for (TestJoinedItem foundJoinedItem : foundItem.getJoinedItems()) {
			TestJoinedItem expectedJoinedItem = expectedJoinedItems.remove(foundJoinedItem.getId());
			assertThat(expectedJoinedItem).isNotNull();
			assertThat(foundJoinedItem.getId()).isEqualTo(expectedJoinedItem.getId());
			assertThat(foundJoinedItem.getName()).isEqualTo(expectedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItem()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
		}
		assertThat(expectedJoinedItems).hasSize(0);
	}

	@Test
	void findByIdWithJoinedItemsOrderByPriceDescNameValueIdAscLimit10() {
		List<TestJoinedItem> expectedJoinedItems = new ArrayList<>();
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(1L, "A", "A", 10.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(2L, "B", "B", 20.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(3L, "C", "C", 30.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(4L, "D", "D", 50.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(5L, "D", "D", 50.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(6L, "F", "G", 90.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(7L, "F", "F", 90.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(8L, "I", "H", 90.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(9L, "H", "I", 90.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(10L, "J", "J", 100.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(11L, "K", "K", 110.0)));
		List<Long> joinedItemIds = expectedJoinedItems.stream().map(TestJoinedItem::getId).toList();
		TestItem expectedItem = this.repository.save(new TestItem(1L, joinedItemIds));
		TestItem foundItem = this.repository.findById(1L).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getJoinedItemsOrderByPriceDescNameValueIdAscLimit10()).hasSize(10);
		assertThat(foundItem.getJoinedItemsOrderByPriceDescNameValueIdAscLimit10()).extracting(TestJoinedItem::getId)
			.containsExactly(11L, 10L, 7L, 6L, 9L, 8L, 4L, 5L, 3L, 2L);
	}

	@Test
	void findByIdWithJoinedItemsOrderByPriceDescIdAscLimit5() {
		List<TestJoinedItem> expectedJoinedItems = new ArrayList<>();
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(1L, 10.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(2L, 20.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(3L, 40.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(4L, 40.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(5L, 50.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(6L, 60.0)));
		List<Long> joinedItemIds = expectedJoinedItems.stream().map(TestJoinedItem::getId).toList();
		TestItem expectedItem = this.repository.save(new TestItem(1L, joinedItemIds));
		TestItem foundItem = this.repository.findById(1L).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getJoinedItemsOrderByPriceDescIdAscLimit5()).hasSize(5);
		assertThat(foundItem.getJoinedItemsOrderByPriceDescIdAscLimit5()).extracting(TestJoinedItem::getId)
			.containsExactly(6L, 5L, 3L, 4L, 2L);
	}

	@Test
	void findByIdWithJoinedItemsOrderByPriceDescIdAsc() {
		List<TestJoinedItem> expectedJoinedItems = new ArrayList<>();
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(1L, 10.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(2L, 30.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(3L, 30.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(4L, 40.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(5L, 50.0)));
		List<Long> joinedItemIds = expectedJoinedItems.stream().map(TestJoinedItem::getId).toList();
		TestItem expectedItem = this.repository.save(new TestItem(1L, joinedItemIds));
		TestItem foundItem = this.repository.findById(1L).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getJoinedItemsOrderByPriceDescIdAsc()).hasSize(5);
		assertThat(foundItem.getJoinedItemsOrderByPriceDescIdAsc()).extracting(TestJoinedItem::getId)
			.containsExactly(5L, 4L, 2L, 3L, 1L);
	}

	@Test
	void findByIdWithJoinedItemsFromRepositoryOrderByPriceDescIdAsc() {
		List<TestJoinedItem> expectedJoinedItems = new ArrayList<>();
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(1L, 10.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(2L, 30.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(3L, 30.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(4L, 40.0)));
		expectedJoinedItems.add(this.joinedItemRepository.save(new TestJoinedItem(5L, 50.0)));
		List<Long> joinedItemIds = expectedJoinedItems.stream().map(TestJoinedItem::getId).toList();
		TestItem expectedItem = this.repository.save(new TestItem(1L, joinedItemIds));
		TestItem foundItem = this.repository.findById(1L).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getJoinedItemsFromRepositoryOrderByPriceDescIdAsc()).hasSize(5);
		assertThat(foundItem.getJoinedItemsFromRepositoryOrderByPriceDescIdAsc()).extracting(TestJoinedItem::getId)
			.containsExactly(5L, 4L, 2L, 3L, 1L);
	}

	// gh-75
	@Test
	void throwsLazyLoadingExceptionWhenDataSourceUnavailable() {
		this.repository.save(TestItem.builder().id(1L).joinedItemId(2L).build());
		this.joinedItemRepository.save(new TestJoinedItem(2L, "TestName"));
		TestItem found = this.repository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getJoinedItemLazy()).isNotNull();
		// disable proxy to simulate Reindexer outage and verify that LazyLoadingException
		// is thrown when accessing lazy namespace reference.
		ReindexerTestContainer.disable();
		assertThatExceptionOfType(LazyLoadingException.class).isThrownBy(() -> found.getJoinedItemLazy().getName())
			.withMessage("Unable to lazily resolve reference")
			.havingCause()
			.withMessage("Connection timeout: no available data source to connect");
		// enable proxy and verify that no exception is thrown when accessing lazy
		// namespace reference.
		ReindexerTestContainer.enable();
		assertThatNoException().isThrownBy(() -> found.getJoinedItemLazy().getName());
	}

	@Test
	void findByIdWhenMandatoryItemIdNullThenDataIntegrityViolationException() {
		this.itemContainerRepository.save(TestItemContainer.builder().id(1L).build());
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.itemContainerRepository.findById(1L));
	}

	@Test
	void getMandatoryItemWhenNotFoundThenEmptyResultDataAccessException() {
		this.itemContainerRepository.save(TestItemContainer.builder().id(1L).mandatoryItemId(1L).build());
		TestItemContainer found = this.itemContainerRepository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getMandatoryItem()).isNotNull();
		assertThatExceptionOfType(LazyLoadingException.class).isThrownBy(() -> found.getMandatoryItem().getName())
			.withCauseInstanceOf(EmptyResultDataAccessException.class);
	}

	@Test
	void getMandatoryItemLookupWhenNotFoundThenEmptyResultDataAccessException() {
		this.itemContainerRepository.save(TestItemContainer.builder().id(1L).mandatoryItemId(1L).build());
		TestItemContainer found = this.itemContainerRepository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getMandatoryItemLookup()).isNotNull();
		assertThatExceptionOfType(LazyLoadingException.class).isThrownBy(() -> found.getMandatoryItemLookup().getName())
			.withCauseInstanceOf(EmptyResultDataAccessException.class);
	}

	@Test
	void getAmbiguousItemWhenMultipleFoundThenIncorrectResultSizeDataAccessException() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.itemContainerRepository
			.save(TestItemContainer.builder().id(1L).mandatoryItemId(1L).ambiguousItemName("TestName").build());
		TestItemContainer found = this.itemContainerRepository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getAmbiguousItem()).isNotNull();
		assertThatExceptionOfType(LazyLoadingException.class).isThrownBy(() -> found.getAmbiguousItem().getName())
			.withCauseInstanceOf(IncorrectResultSizeDataAccessException.class);
	}

	@Test
	void getAmbiguousItemLookupWhenMultipleFoundThenIncorrectResultSizeDataAccessException() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.itemContainerRepository
			.save(TestItemContainer.builder().id(1L).mandatoryItemId(1L).ambiguousItemName("TestName").build());
		TestItemContainer found = this.itemContainerRepository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getAmbiguousItemLookup()).isNotNull();
		assertThatExceptionOfType(LazyLoadingException.class).isThrownBy(() -> found.getAmbiguousItemLookup().getName())
			.withCauseInstanceOf(IncorrectResultSizeDataAccessException.class);
	}

	@Test
	void getJoinedItemsByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName1").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName2").build());
		this.itemContainerRepository.save(TestItemContainer.builder()
			.id(1L)
			.mandatoryItemId(1L)
			.joinedItemNames(List.of("TestName1", "TestName2"))
			.build());
		TestItemContainer found = this.itemContainerRepository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getJoinedItemsByName()).hasSize(2);
		assertThat(found.getJoinedItemsByName()).extracting(TestItem::getName)
			.containsExactlyInAnyOrder("TestName1", "TestName2");
	}

}
