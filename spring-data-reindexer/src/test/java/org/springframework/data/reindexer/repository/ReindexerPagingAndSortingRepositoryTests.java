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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}'s paging and sorting support.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
class ReindexerPagingAndSortingRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Test
	void findAllPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = Pageable.ofSize(5);
		do {
			Page<TestItem> foundItems = this.repository.findAll(pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		}
		while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findByIdInPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = Pageable.ofSize(5);
		List<Long> expectedIds = expectedItems.stream().map(TestItem::getId).toList();
		long totalCount = this.repository.countByIdIn(expectedIds);
		do {
			List<TestItem> foundItems = this.repository.findByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = new PageImpl<>(foundItems, pageable, totalCount).nextPageable();
		}
		while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findPageByIdIn() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = Pageable.ofSize(5);
		List<Long> expectedIds = expectedItems.stream().map(TestItem::getId).toList();
		do {
			Page<TestItem> foundItems = this.repository.findPageByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		}
		while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findFirst2By() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst2By(PageRequest.of(0, 3, Sort.Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst2By(PageRequest.of(1, 3, Sort.Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	void findFirst3By() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst3By(PageRequest.of(0, 2, Sort.Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst3By(PageRequest.of(1, 2, Sort.Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	void findAllCountByIdInPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Order.desc("id"), Sort.Order.asc("name")));
		List<Long> expectedIds = expectedItems.stream().map(TestItem::getId).toList();
		do {
			Page<TestItem> foundItems = this.repository.findAllCountByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		}
		while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findPageByIdInNativeSql() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Order.desc("id"), Sort.Order.asc("name")));
		List<Long> expectedIds = expectedItems.stream().map(TestItem::getId).toList();
		do {
			Page<TestItem> foundItems = this.repository.findPageByIdInNativeSql(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertThat(expectedItems.remove(item)).isTrue();
			}
			pageable = foundItems.nextPageable();
		}
		while (pageable.isPaged());
		assertThat(expectedItems).isEmpty();
	}

	@Test
	void findAllCountCachedByIdInPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Order.desc("id"), Sort.Order.asc("name")));
		List<Long> expectedIds = expectedItems.stream().map(TestItem::getId).toList();
		do {
			Page<TestItem> foundItems = this.repository.findAllCountCachedByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		}
		while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findFirst2Sql() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst2Sql(PageRequest.of(0, 3, Sort.Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst2Sql(PageRequest.of(1, 3, Sort.Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	void findFirst3Sql() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst3Sql(PageRequest.of(0, 2, Sort.Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst3Sql(PageRequest.of(1, 2, Sort.Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	void findAllSlice() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		this.repository.save(TestItem.builder().id(4L).build());
		this.repository.save(TestItem.builder().id(5L).build());
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllBy(pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertTrue(foundItems.hasNext());
	}

	@Test
	void findAllSliceWhenNoMorePages() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllBy(pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertFalse(foundItems.hasNext());
	}

	@Test
	void findAllByIdInSlice() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		this.repository.save(TestItem.builder().id(4L).build());
		this.repository.save(TestItem.builder().id(5L).build());
		List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllByIdIn(ids, pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertTrue(foundItems.hasNext());
	}

	@Test
	void findAllSliceSql() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		this.repository.save(TestItem.builder().id(4L).build());
		this.repository.save(TestItem.builder().id(5L).build());
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllSliceSql(pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertTrue(foundItems.hasNext());
	}

	@Test
	void findAllSliceSqlWhenNoMorePages() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllSliceSql(pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertFalse(foundItems.hasNext());
	}

	@Test
	void findAllByIdInSliceSql() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		this.repository.save(TestItem.builder().id(4L).build());
		this.repository.save(TestItem.builder().id(5L).build());
		List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllByIdInSliceSql(ids, pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertTrue(foundItems.hasNext());
	}

	@Test
	void findAllByIdInSliceNativeSql() {
		this.repository.save(TestItem.builder().id(1L).build());
		this.repository.save(TestItem.builder().id(2L).build());
		this.repository.save(TestItem.builder().id(3L).build());
		this.repository.save(TestItem.builder().id(4L).build());
		this.repository.save(TestItem.builder().id(5L).build());
		List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
		Pageable pageable = PageRequest.of(0, 3);
		Slice<TestItem> foundItems = this.repository.findAllByIdInSliceNativeSql(ids, pageable);
		assertNotNull(foundItems);
		assertEquals(3, foundItems.getNumberOfElements());
		assertTrue(foundItems.hasNext());
	}

	@Test
	void findAllByIdInSortedByIdInAscOrder() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAllByIdIn(expectedItems.stream().map(TestItem::getId).toList(),
				Sort.by(Sort.Direction.ASC, "id"));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < expectedItems.size(); i++) {
			assertEquals(expectedItems.get(i), foundItems.get(i));
		}
	}

	@Test
	void findAllByIdInSortedByIdInDescOrder() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAllByIdIn(expectedItems.stream().map(TestItem::getId).toList(),
				Sort.by(Sort.Direction.DESC, "id"));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < expectedItems.size(); i++) {
			assertEquals(expectedItems.get(i), foundItems.get(foundItems.size() - 1 - i));
		}
	}

}
