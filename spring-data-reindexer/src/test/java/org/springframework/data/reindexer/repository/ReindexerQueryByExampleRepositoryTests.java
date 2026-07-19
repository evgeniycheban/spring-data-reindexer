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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.dto.TestItemDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemProjection;
import org.springframework.data.reindexer.repository.item.dto.TestItemRecord;
import org.springframework.data.reindexer.repository.item.dto.TestNestedItem;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.repository.query.FluentQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}'s Query By Example (QBE) methods.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
class ReindexerQueryByExampleRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Test
	void findOneByExample() {
		TestNestedItem nestedItem = new TestNestedItem("TestNestedName", "TestNestedValue");
		TestItem expectedItem = this.repository.save(new TestItem(1L, nestedItem, "TestName", "TestValue"));
		TestItem foundItem = this.repository.findOne(Example.of(expectedItem)).orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.getId());
		assertEquals(expectedItem.getName(), foundItem.getName());
		assertEquals(expectedItem.getValue(), foundItem.getValue());
		assertNotNull(foundItem.getNestedItem());
		assertEquals(nestedItem.getName(), foundItem.getNestedItem().getName());
		assertEquals(nestedItem.getValue(), foundItem.getNestedItem().getValue());
	}

	@Test
	void findAllByExample() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue2"));
		List<TestItem> items = this.repository.findAll(Example.of(new TestItem(null, null, "TestValue1")));
		assertNotNull(items);
		assertEquals(2, items.size());
	}

	@Test
	void findAllPageableByExample() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue2"));
		Page<TestItem> items = this.repository.findAll(Example.of(new TestItem(null, null, "TestValue1")),
				PageRequest.of(0, 1));
		assertNotNull(items);
		assertEquals(1, items.getSize());
	}

	@Test
	void findAllSortByExample() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue2"));
		List<TestItem> foundItems = this.repository.findAll(Example.of(new TestItem(null, null, "TestValue1")),
				Sort.by(Sort.Direction.DESC, "id"));
		assertNotNull(foundItems);
		List<Long> ids = foundItems.stream().map(TestItem::getId).toList();
		assertThat(ids).containsExactly(3L, 2L, 1L);
	}

	@Test
	void existsByExample() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository.exists(Example.of(new TestItem(null, null, "TestValue")));
		assertTrue(exists);
	}

	@Test
	void countByExample() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue2"));
		long count = this.repository.count(Example.of(new TestItem(null, null, "TestValue1")));
		assertEquals(2, count);
	}

	@Test
	void findByFluentQueryExampleClassProjection() {
		TestItem expectedItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItemDto foundItem = this.repository
			.findBy(Example.of(expectedItem), query -> query.project(List.of("id", "name")).as(TestItemDto.class).one())
			.orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.getId());
		assertEquals(expectedItem.getName(), foundItem.getName());
	}

	@Test
	void findByFluentQueryExampleSort() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue2"));
		List<TestItemDto> foundItems = this.repository
			.findBy(Example.of(new TestItem(null, null, "TestValue1")),
					query -> query.project("id", "name").as(TestItemDto.class))
			.sortBy(Sort.by(Sort.Direction.DESC, "id"))
			.all();
		assertNotNull(foundItems);
		List<Long> ids = foundItems.stream().map(TestItemDto::getId).toList();
		assertThat(ids).containsExactly(3L, 2L, 1L);
	}

	@Test
	void findByFluentQueryExampleSortFirst() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue1"));
		TestItemDto foundItem = this.repository
			.findBy(Example.of(new TestItem(null, null, "TestValue1")),
					query -> query.project("id", "name").as(TestItemDto.class))
			.sortBy(Sort.by(Sort.Direction.DESC, "id"))
			.firstValue();
		assertNotNull(foundItem);
		assertEquals(3L, foundItem.getId());
	}

	@Test
	void findByFluentQueryExampleSortPage() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue1"));
		Page<TestItemDto> foundItems = this.repository
			.findBy(Example.of(new TestItem(null, null, "TestValue1")),
					query -> query.project("id", "name").as(TestItemDto.class))
			.sortBy(Sort.by(Sort.Direction.DESC, "id"))
			.page(PageRequest.of(0, 2));
		assertNotNull(foundItems);
		List<Long> ids = foundItems.stream().map(TestItemDto::getId).toList();
		assertThat(ids).containsExactly(3L, 2L);
	}

	@Test
	void findByFluentQueryExamplePageSort() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue1"));
		Page<TestItemDto> foundItems = this.repository
			.findBy(Example.of(new TestItem(null, null, "TestValue1")),
					query -> query.project("id", "name").as(TestItemDto.class))
			.page(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id")));
		assertNotNull(foundItems);
		List<Long> ids = foundItems.stream().map(TestItemDto::getId).toList();
		assertThat(ids).containsExactly(3L, 2L);
	}

	@Test
	void findByFluentQueryExampleSortPageSort() {
		this.repository.save(new TestItem(1L, "A", "TestValue1", true));
		this.repository.save(new TestItem(2L, "B", "TestValue1", true));
		this.repository.save(new TestItem(3L, "C", "TestValue2", true));
		this.repository.save(new TestItem(4L, "C", "TestValue2", false));
		Page<TestItem> foundItems = this.repository.findBy(Example.of(new TestItem(null, null, null, true)),
				query -> query.sortBy(Sort.by(Sort.Direction.DESC, "value"))
					.page(PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "name"))));
		assertNotNull(foundItems);
		List<Long> ids = foundItems.stream().map(TestItem::getId).toList();
		assertThat(ids).containsExactly(3L, 1L, 2L);
	}

	@Test
	void findByFluentQueryExampleCount() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue2"));
		long count = this.repository.findBy(Example.of(new TestItem(null, null, "TestValue1")),
				FluentQuery.FetchableFluentQuery::count);
		assertEquals(2, count);
	}

	@Test
	void findByFluentQueryExampleLimit() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue1"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue2"));
		long count = this.repository.findBy(Example.of(new TestItem(null, null, "TestValue1")),
				query -> query.limit(1).count());
		assertEquals(1, count);
	}

	@Test
	void findByFluentQueryExampleExists() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository.findBy(Example.of(new TestItem(null, null, "TestValue")),
				FluentQuery.FetchableFluentQuery::exists);
		assertTrue(exists);
	}

	@Test
	void findByFluentQueryExampleRecordProjection() {
		TestItem expectedItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItemRecord foundItem = this.repository
			.findBy(Example.of(expectedItem),
					query -> query.project(List.of("id", "name")).as(TestItemRecord.class).one())
			.orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.id());
		assertEquals(expectedItem.getName(), foundItem.name());
	}

	@Test
	void findByFluentQueryExampleInterfaceProjection() {
		TestItem expectedItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItemProjection foundItem = this.repository
			.findBy(Example.of(expectedItem),
					query -> query.project(List.of("id", "name")).as(TestItemProjection.class).one())
			.orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.getId());
		assertEquals(expectedItem.getName(), foundItem.getName());
	}

	@Test
	void findOneByExampleMatcherContaining() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		Optional<TestItem> foundItem = this.repository.findOne(Example.of(new TestItem(null, null, "est"),
				ExampleMatcher.matching()
					.withMatcher("value",
							ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.CONTAINING))));
		assertNotNull(foundItem);
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findAllByExampleMatcherContaining() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "Value"));
		List<TestItem> foundItems = this.repository.findAll(Example.of(new TestItem(null, null, "est"),
				ExampleMatcher.matching()
					.withMatcher("value",
							ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.CONTAINING))));
		assertNotNull(foundItems);
		assertEquals(3, foundItems.size());
	}

	@Test
	void findAllByExampleMatcherIgnorePathsContaining() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "Value"));
		List<TestItem> foundItems = this.repository.findAll(Example.of(new TestItem(null, "est", "est"), ExampleMatcher
			.matchingAll()
			.withIgnorePaths("id")
			.withMatcher("name", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.CONTAINING))
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.CONTAINING))));
		assertNotNull(foundItems);
		assertEquals(3, foundItems.size());
	}

	@Test
	void existsByExampleMatcherContaining() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository.exists(Example.of(new TestItem(null, null, "est"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.CONTAINING))));
		assertTrue(exists);
	}

	@Test
	void countByExampleMatcherContaining() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "Value"));
		long count = this.repository.count(Example.of(new TestItem(null, null, "est"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.CONTAINING))));
		assertEquals(3, count);
	}

	@Test
	void findOneByExampleMatcherStarting() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		Optional<TestItem> foundItem = this.repository.findOne(Example.of(new TestItem(null, null, "Test"),
				ExampleMatcher.matching()
					.withMatcher("value",
							ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.STARTING))));
		assertNotNull(foundItem);
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findAllByExampleMatcherStarting() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "Value"));
		List<TestItem> foundItems = this.repository.findAll(Example.of(new TestItem(null, null, "Test"),
				ExampleMatcher.matching()
					.withMatcher("value",
							ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.STARTING))));
		assertNotNull(foundItems);
		assertEquals(3, foundItems.size());
	}

	@Test
	void existsByExampleMatcherStarting() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository.exists(Example.of(new TestItem(null, null, "Test"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.STARTING))));
		assertTrue(exists);
	}

	@Test
	void countByExampleMatcherStarting() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "Value"));
		long count = this.repository.count(Example.of(new TestItem(null, null, "Test"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.STARTING))));
		assertEquals(3, count);
	}

	@Test
	void findOneByExampleMatcherEnding() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		Optional<TestItem> foundItem = this.repository
			.findOne(Example.of(new TestItem(null, null, "Value"), ExampleMatcher.matching()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.ENDING))));
		assertNotNull(foundItem);
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findAllByExampleMatcherEnding() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue1"));
		List<TestItem> foundItems = this.repository
			.findAll(Example.of(new TestItem(null, null, "Value"), ExampleMatcher.matching()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.ENDING))));
		assertNotNull(foundItems);
		assertEquals(3, foundItems.size());
	}

	@Test
	void existsByExampleMatcherEnding() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository.exists(Example.of(new TestItem(null, null, "Value"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.ENDING))));
		assertTrue(exists);
	}

	@Test
	void countByExampleMatcherEnding() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue1"));
		long count = this.repository.count(Example.of(new TestItem(null, null, "Value"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.ENDING))));
		assertEquals(3, count);
	}

	@Test
	void findOneByExampleMatcherExact() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		Optional<TestItem> foundItem = this.repository
			.findOne(Example.of(new TestItem(null, null, "TestValue"), ExampleMatcher.matching()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertNotNull(foundItem);
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findAllByExampleMatcherExact() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue1"));
		List<TestItem> foundItems = this.repository
			.findAll(Example.of(new TestItem(null, null, "TestValue"), ExampleMatcher.matching()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertNotNull(foundItems);
		assertEquals(3, foundItems.size());
	}

	@Test
	void existsByExampleMatcherExact() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository
			.exists(Example.of(new TestItem(null, null, "TestValue"), ExampleMatcher.matching()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertTrue(exists);
	}

	@Test
	void countByExampleMatcherExact() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue1"));
		long count = this.repository.count(Example.of(new TestItem(null, null, "TestValue"), ExampleMatcher.matching()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertEquals(3, count);
	}

	@Test
	void findOneByExampleMatcherExactIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestItem", "TestValue"));
		Optional<TestItem> foundItem = this.repository
			.findOne(Example.of(new TestItem(null, null, "testvalue"), ExampleMatcher.matching()
				.withIgnoreCase()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findOneByExampleWhenPropertySpecifierIgnoreCaseThenPropertySpecifierTakesPrecedence() {
		this.repository.save(new TestItem(1L, "TestItem", "TestValue"));
		Optional<TestItem> foundItem = this.repository.findOne(
				Example.of(new TestItem(null, null, "testvalue"), ExampleMatcher.matching().withIgnoreCase("value")));
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findAllByExampleMatcherExactIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue1"));
		List<TestItem> foundItems = this.repository
			.findAll(Example.of(new TestItem(null, null, "testvalue"), ExampleMatcher.matching()
				.withIgnoreCase()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertNotNull(foundItems);
		assertEquals(3, foundItems.size());
	}

	@Test
	void existsByExampleMatcherExactIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		boolean exists = this.repository
			.exists(Example.of(new TestItem(null, null, "testvalue"), ExampleMatcher.matching()
				.withIgnoreCase()
				.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertTrue(exists);
	}

	@Test
	void countByExampleMatcherExactIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue"));
		this.repository.save(new TestItem(4L, "TestName", "TestValue1"));
		long count = this.repository.count(Example.of(new TestItem(null, null, "testvalue"), ExampleMatcher.matching()
			.withIgnoreCase()
			.withMatcher("value", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.EXACT))));
		assertEquals(3, count);
	}

}
