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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import ru.rt.restream.reindexer.AggregationResult;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.TestJoinedItemRepository;
import org.springframework.data.reindexer.repository.item.dto.TestNestedItem;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.item.entity.TestJoinedItem;
import org.springframework.data.reindexer.repository.query.ReindexerResultAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}'s string-based query methods.
 *
 * @author Evgeniy Cheban
 */
class ReindexerStringBasedQueryRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Autowired
	TestJoinedItemRepository joinedItemRepository;

	@Test
	void findIteratorSqlByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestValue", null));
		try (ResultIterator<TestItem> it = this.repository.findIteratorSqlByName("TestValue")) {
			assertTrue(it.hasNext());
			TestItem item = it.next();
			assertEquals(testItem.getId(), item.getId());
			assertEquals(testItem.getName(), item.getName());
			assertEquals(testItem.getValue(), item.getValue());
			assertFalse(it.hasNext());
		}
	}

	@Test
	void findIteratorNativeSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		try (ResultIterator<TestItem> it = this.repository.findIteratorNativeSqlByName("TestName")) {
			assertThat(it.hasNext()).isTrue();
			TestItem item = it.next();
			assertThat(item).isNotNull();
			assertThat(item.getId()).isEqualTo(1L);
			assertThat(item.getName()).isEqualTo("TestName");
			assertThat(it.hasNext()).isFalse();
		}
	}

	@Test
	void findIteratorSqlByNameParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		try (ResultIterator<TestItem> it = this.repository.findIteratorSqlByNameParam("TestName")) {
			assertTrue(it.hasNext());
			TestItem item = it.next();
			assertEquals(testItem.getId(), item.getId());
			assertEquals(testItem.getName(), item.getName());
			assertEquals(testItem.getValue(), item.getValue());
			assertFalse(it.hasNext());
		}
	}

	@Test
	void countSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		long count = this.repository.countSqlByName("TestName");
		assertThat(count).isEqualTo(2);
	}

	@Test
	void sumSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		long sum = this.repository.sumSqlByName("TestName");
		assertThat(sum).isEqualTo(6L);
	}

	@Test
	void minSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		long min = this.repository.minSqlByName("TestName");
		assertThat(min).isEqualTo(1L);
	}

	@Test
	void maxSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		long max = this.repository.maxSqlByName("TestName");
		assertThat(max).isEqualTo(3L);
	}

	@Test
	void avgSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		long avg = this.repository.avgSqlByName("TestName");
		assertThat(avg).isEqualTo(2L);
	}

	@Test
	void facetOrderByIdDescSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		ReindexerResultAccessor<TestItem> accessor = this.repository.facetOrderByIdDescSqlByName("TestName");
		try (accessor) {
			AggregationResult result = accessor.aggregationResult("facet", "id");
			assertThat(result).isNotNull();
			assertThat(result.getFacets()).hasSize(3);
			assertThat(result.getFacets()).flatMap(AggregationResult.Facet::getValues)
				.containsExactly("3", "TestName", "2", "TestName", "1", "TestName");
		}
	}

	@Test
	void findOneSqlByIdGreaterThan() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		List<TestItem> found = this.repository.findOneSqlByIdGreaterThan(1L);
		assertThat(found).hasSize(2);
		assertThat(found).extracting(TestItem::getId).containsExactly(2L, 3L);
	}

	@Test
	void findOneSqlByIdGreaterEqualThan() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		List<TestItem> found = this.repository.findOneSqlByIdGreaterEqualThan(1L);
		assertThat(found).hasSize(3);
		assertThat(found).extracting(TestItem::getId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void findOneSqlByIdLessThan() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		List<TestItem> found = this.repository.findOneSqlByIdLessThan(3L);
		assertThat(found).hasSize(2);
		assertThat(found).extracting(TestItem::getId).containsExactly(1L, 2L);
	}

	@Test
	void findOneSqlByIdLessEqualThan() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		this.repository.save(TestItem.builder().id(3L).name("TestName").build());
		List<TestItem> found = this.repository.findOneSqlByIdLessEqualThan(3L);
		assertThat(found).hasSize(3);
		assertThat(found).extracting(TestItem::getId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void getOneSqlByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.getOneSqlByName("TestName");
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void getOneNativeSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		TestItem item = this.repository.getOneNativeSqlByName("TestName");
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo("TestName");
	}

	@Test
	void getOneSqlByNameParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.getOneSqlByNameParam("TestName");
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneSqlByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findOneSqlByName("TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneSqlByNestedNameAndValue() {
		this.repository.save(TestItem.builder().id(1L).nestedItem(new TestNestedItem("TestName", "TestValue")).build());
		TestItem item = this.repository.findOneSqlByNestedNameAndValue("TestName", "TestValue").orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getNestedItem()).isNotNull();
		assertThat(item.getNestedItem().getName()).isEqualTo("TestName");
		assertThat(item.getNestedItem().getValue()).isEqualTo("TestValue");
	}

	@Test
	void findOneNativeSqlByName() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		TestItem item = this.repository.findOneNativeSqlByName("TestName").orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo("TestName");
	}

	@Test
	void findOneNativeSqlByNameQuotedAndValue() {
		this.repository.save(TestItem.builder().id(1L).name(":name").value("TestValue").build());
		TestItem item = this.repository.findOneNativeSqlByNameQuotedAndValue("TestValue").orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo(":name");
		assertThat(item.getValue()).isEqualTo("TestValue");
	}

	@Test
	void findOneByNameNotNull() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").build());
		TestItem found = this.repository.findOneSqlByNameNotNull().orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
	}

	@Test
	void findOneByNameNull() {
		this.repository.save(TestItem.builder().id(1L).build());
		TestItem found = this.repository.findOneSqlByNameNull().orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isNull();
	}

	@Test
	void findOneSqlByNameEqValue() {
		this.repository.save(TestItem.builder().id(1L).name("TestNameValue").value("TestNameValue").build());
		TestItem found = this.repository.findOneSqlByNameEqValue("TestNameValue").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestNameValue");
		assertThat(found.getValue()).isEqualTo("TestNameValue");
	}

	@Test
	void findAllBySubQueryName() {
		this.repository.save(TestItem.builder().id(1L).name("TestLeftName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestRightName").build());
		TestItem found = this.repository.findAllBySubQueryName("TestLeftName", "TestRightName").orElse(null);
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestLeftName");
	}

	@Test
	void findAllNativeSqlByMergeQueryName() {
		this.repository.save(TestItem.builder().id(1L).name("TestLeftName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestRightName").build());
		List<TestItem> found = this.repository.findAllNativeSqlByMergeQueryName("TestLeftName", "TestRightName");
		assertThat(found).hasSize(2);
		assertThat(found).element(0).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(1L);
			assertThat(it.getName()).isEqualTo("TestLeftName");
		});
		assertThat(found).element(1).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(2L);
			assertThat(it.getName()).isEqualTo("TestRightName");
		});
	}

	@Test
	void findAllSqlByMergeQueryName() {
		this.repository.save(TestItem.builder().id(1L).name("TestLeftName").build());
		this.repository.save(TestItem.builder().id(2L).name("TestRightName").build());
		List<TestItem> found = this.repository.findAllSqlByMergeQueryName("TestLeftName", "TestRightName");
		assertThat(found).hasSize(2);
		assertThat(found).element(0).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(1L);
			assertThat(it.getName()).isEqualTo("TestLeftName");
		});
		assertThat(found).element(1).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(2L);
			assertThat(it.getName()).isEqualTo("TestRightName");
		});
	}

	@Test
	void findAllSqlByMergeQueryNameQuotedParenthesisString() {
		this.repository.save(TestItem.builder().id(1L).name("MERGE(SELECT * FROM items WHERE name = :name)").build());
		this.repository.save(TestItem.builder().id(2L).name("TestName").build());
		List<TestItem> found = this.repository.findAllSqlByMergeQueryNameQuotedParenthesisString("TestName");
		assertThat(found).hasSize(4);
		assertThat(found).element(0).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(1L);
			assertThat(it.getName()).isEqualTo("MERGE(SELECT * FROM items WHERE name = :name)");
		});
		assertThat(found).element(1).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(2L);
			assertThat(it.getName()).isEqualTo("TestName");
		});
		assertThat(found).element(2).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(1L);
			assertThat(it.getName()).isEqualTo("MERGE(SELECT * FROM items WHERE name = :name)");
		});
		assertThat(found).element(3).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(2L);
			assertThat(it.getName()).isEqualTo("TestName");
		});
	}

	@Test
	void findOneWithSimpleJoinAliasSqlByName() {
		this.joinedItemRepository.save(new TestJoinedItem(1L, "TestJoinedName"));
		this.repository.save(TestItem.builder().id(1L).name("TestName").joinedItemId(1L).build());
		TestItem found = this.repository.findOneWithSimpleJoinAliasSqlByName("TestName").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
		assertThat(found.getJoinedItemId()).isEqualTo(1L);
		assertThat(found.getJoinedItem()).isNotNull();
		assertThat(found.getJoinedItem().getId()).isEqualTo(1L);
		assertThat(found.getJoinedItem().getName()).isEqualTo("TestJoinedName");
	}

	@Test
	void findOneWithSimpleJoinFullNameSqlByName() {
		this.joinedItemRepository.save(new TestJoinedItem(1L, "TestJoinedName"));
		this.repository.save(TestItem.builder().id(1L).name("TestName").joinedItemId(1L).build());
		TestItem found = this.repository.findOneWithSimpleJoinFullNameSqlByName("TestName").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
		assertThat(found.getJoinedItemId()).isEqualTo(1L);
		assertThat(found.getJoinedItem()).isNotNull();
		assertThat(found.getJoinedItem().getId()).isEqualTo(1L);
		assertThat(found.getJoinedItem().getName()).isEqualTo("TestJoinedName");
	}

	@Test
	void findOneWithConditionalJoinSqlByName() {
		this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName"));
		this.repository.save(TestItem.builder().id(1L).name("TestName").joinedItemId(1L).build());
		TestItem found = this.repository.findOneWithConditionalJoinSqlByName("TestName").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
		assertThat(found.getJoinedItemId()).isEqualTo(1L);
		assertThat(found.getJoinedItem()).isNotNull();
		assertThat(found.getJoinedItem().getId()).isEqualTo(1L);
		assertThat(found.getJoinedItem().getName()).isEqualTo("TestName");
	}

	@Test
	void findOneWithMultipleOnJoinSqlByName() {
		this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName"));
		this.repository.save(TestItem.builder().id(1L).name("TestName").joinedItemId(1L).build());
		TestItem found = this.repository.findOneWithMultipleOnJoinSqlByName("TestName").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
		assertThat(found.getJoinedItemId()).isEqualTo(1L);
		assertThat(found.getJoinedItem()).isNotNull();
		assertThat(found.getJoinedItem().getId()).isEqualTo(1L);
		assertThat(found.getJoinedItem().getName()).isEqualTo("TestName");
	}

	@Test
	void findOneWithSimpleJoinInSqlByName() {
		this.joinedItemRepository.save(new TestJoinedItem(1L, "TestJoinedName1"));
		this.joinedItemRepository.save(new TestJoinedItem(2L, "TestJoinedName2"));
		this.repository.save(TestItem.builder().id(1L).name("TestName").joinedItemIds(List.of(1L, 2L)).build());
		TestItem found = this.repository.findOneWithSimpleJoinInSqlByName("TestName").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
		assertThat(found.getJoinedItemIds()).hasSize(2);
		assertThat(found.getJoinedItems()).element(0).satisfies((joined) -> {
			assertThat(joined.getId()).isEqualTo(1L);
			assertThat(joined.getName()).isEqualTo("TestJoinedName1");
		});
		assertThat(found.getJoinedItems()).element(1).satisfies((joined) -> {
			assertThat(joined.getId()).isEqualTo(2L);
			assertThat(joined.getName()).isEqualTo("TestJoinedName2");
		});
	}

	@Test
	void findOneWithConditionalJoinInSqlByName() {
		this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName"));
		this.joinedItemRepository.save(new TestJoinedItem(2L, "TestName"));
		this.repository.save(TestItem.builder().id(1L).name("TestName").joinedItemIds(List.of(1L, 2L)).build());
		TestItem found = this.repository.findOneWithConditionalJoinInSqlByName("TestName").orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(1L);
		assertThat(found.getName()).isEqualTo("TestName");
		assertThat(found.getJoinedItemIds()).hasSize(2);
		assertThat(found.getJoinedItems()).element(0).satisfies((joined) -> {
			assertThat(joined.getId()).isEqualTo(1L);
			assertThat(joined.getName()).isEqualTo("TestName");
		});
		assertThat(found.getJoinedItems()).element(1).satisfies((joined) -> {
			assertThat(joined.getId()).isEqualTo(2L);
			assertThat(joined.getName()).isEqualTo("TestName");
		});
	}

	@Test
	void findOneSqlByNameParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findOneSqlByNameParam("TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneSqlByNameManyParameters() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository
			.findOneSqlByNameAndValueManyParams(null, null, null, null, null, null, null, null, null, null, "TestName",
					"TestValue")
			.orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneSqlByNameOrValue() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValue(1L, "TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneSqlByNameOrValueParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValueParam(1L, "TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneNativeSqlByIdAndNameAndValueParam() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").value("TestValue").build());
		TestItem item = this.repository.findOneNativeSqlByIdAndNameAndValueParam(1L, "TestName", "TestValue")
			.orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo("TestName");
		assertThat(item.getValue()).isEqualTo("TestValue");
	}

	@Test
	void findOneSqlSpelByItemIdAndNameAndValueParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlSpelByItemIdAndNameAndValueParam(testItem).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneNativeSqlSpelByItemIdAndNameAndValueParam() {
		TestItem testItem = this.repository.save(TestItem.builder().id(1L).name("TestName").value("TestValue").build());
		TestItem item = this.repository.findOneNativeSqlSpelByItemIdAndNameAndValueParam(testItem).orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo("TestName");
		assertThat(item.getValue()).isEqualTo("TestValue");
	}

	@Test
	void findOneSqlSpelByIdAndNameAndValueParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlSpelByIdAndNameAndValueParam(1L, "TestName", "TestValue")
			.orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneNativeSqlSpelByIdAndNameAndValueParam() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").value("TestValue").build());
		TestItem item = this.repository.findOneNativeSqlSpelByIdAndNameAndValueParam(1L, "TestName", "TestValue")
			.orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo("TestName");
		assertThat(item.getValue()).isEqualTo("TestValue");
	}

	@Test
	void findOneSqlByIdAndNameAndValueAnyParameterOrder() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValue("TestValue", 1L, "TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findOneSqlByIdAndNameAndValueParamAnyParameterOrder() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValueParam("TestValue", 1L, "TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void updateNameSql() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertNotNull(testItem);
		this.repository.updateNameSql("TestNameUpdated", 1L);
		TestItem item = this.repository.findById(1L).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals("TestNameUpdated", item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void updateNameNativeSql() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").value("TestValue").build());
		this.repository.updateNameNativeSql("TestNameUpdated", 1L);
		TestItem item = this.repository.findById(1L).orElse(null);
		assertThat(item).isNotNull();
		assertThat(item.getId()).isEqualTo(1L);
		assertThat(item.getName()).isEqualTo("TestNameUpdated");
		assertThat(item.getValue()).isEqualTo("TestValue");
	}

	@Test
	void updateNameSqlParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertNotNull(testItem);
		this.repository.updateNameSqlParam("TestNameUpdated", 1L);
		TestItem item = this.repository.findById(1L).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals("TestNameUpdated", item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void updateDefaultDateTimeMinus3DaysUsingExpression() {
		LocalDateTime now = LocalDateTime.now();
		this.repository.save(TestItem.builder().id(1L).defaultDateTime(now).build());
		this.repository.updateDefaultDateTimeMinus3DaysUsingExpression(1L);
		TestItem found = this.repository.findById(1L).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getDefaultDateTime().toLocalDate()).isEqualTo(now.minusDays(3).toLocalDate());
	}

	@Test
	void deleteByNameAndValueSql() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").value("TestValue").build());
		this.repository.deleteByNameAndValueSql("TestName", "TestValue");
		assertThat(this.repository.existsById(1L)).isFalse();
	}

	@Test
	void deleteByNameAndValueNativeSql() {
		this.repository.save(TestItem.builder().id(1L).name("TestName").value("TestValue").build());
		this.repository.deleteByNameAndValueNativeSql("TestName", "TestValue");
		assertThat(this.repository.existsById(1L)).isFalse();
	}

	@Test
	void findAllListSql() {
		Map<Long, TestItem> expectedItems = new HashMap<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.put(i, this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		for (TestItem actual : this.repository.findAllListSql()) {
			TestItem expected = expectedItems.remove(actual.getId());
			assertNotNull(expected);
			assertEquals(expected.getId(), actual.getId());
			assertEquals(expected.getName(), actual.getName());
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findAllSetSql() {
		Map<Long, TestItem> expectedItems = new HashMap<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.put(i, this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		for (TestItem actual : this.repository.findAllSetSql()) {
			TestItem expected = expectedItems.remove(actual.getId());
			assertNotNull(expected);
			assertEquals(expected.getId(), actual.getId());
			assertEquals(expected.getName(), actual.getName());
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findAllStreamSql() {
		Map<Long, TestItem> expectedItems = new HashMap<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.put(i, this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		try (Stream<TestItem> itemStream = this.repository.findAllStreamSql()) {
			itemStream.forEach(actual -> {
				TestItem expected = expectedItems.remove(actual.getId());
				assertNotNull(expected);
				assertEquals(expected.getId(), actual.getId());
				assertEquals(expected.getName(), actual.getName());
				assertEquals(expected.getValue(), actual.getValue());
			});
		}
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findAllSqlLimit() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAllSqlLimit(Limit.of(10));
		for (TestItem item : foundItems) {
			assertTrue(expectedItems.remove(item));
		}
		assertEquals(90, expectedItems.size());
	}

	@Test
	void findByDefaultTimeSql() {
		TestItem expectedItem = TestItem.builder().id(1L).defaultTime(LocalTime.of(15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByDefaultTimeSql(LocalTime.of(15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getDefaultTime()).isEqualTo(expectedItem.getDefaultTime());
	}

	@Test
	void findByDefaultDateSql() {
		TestItem expectedItem = TestItem.builder().id(1L).defaultDate(LocalDate.of(2020, 1, 1)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByDefaultDateSql(LocalDate.of(2020, 1, 1)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getDefaultDate()).isEqualTo(expectedItem.getDefaultDate());
	}

	@Test
	void findByDefaultDateTimeSql() {
		TestItem expectedItem = TestItem.builder().id(1L).defaultDateTime(LocalDateTime.of(2020, 1, 1, 15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByDefaultDateTimeSql(LocalDateTime.of(2020, 1, 1, 15, 30))
			.orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getDefaultDateTime()).isEqualTo(expectedItem.getDefaultDateTime());
	}

	@Test
	void findAllByDefaultDateBetweenNativeSql() {
		AtomicLong id = new AtomicLong(1);
		Map<Long, TestItem> expectedItems = LocalDate.of(2020, 1, 1)
			.datesUntil(LocalDate.of(2020, 6, 1))
			.map(date -> TestItem.builder().id(id.getAndIncrement()).defaultDate(date).build())
			.map(this.repository::save)
			.collect(Collectors.toMap(TestItem::getId, Function.identity()));
		long start = LocalDate.of(2020, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long end = LocalDate.of(2020, 6, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		List<TestItem> foundItems = this.repository.findAllByDefaultDateBetweenNativeSql(start, end);
		assertThat(foundItems).hasSize(expectedItems.size());
		for (TestItem foundItem : foundItems) {
			TestItem expectedItem = expectedItems.remove(foundItem.getId());
			assertThat(expectedItem).isNotNull();
			assertThat(expectedItem.getDefaultDate()).isEqualTo(foundItem.getDefaultDate());
		}
		assertThat(expectedItems).isEmpty();
	}

	@Test
	void findAllByDefaultDateBetweenSql() {
		AtomicLong id = new AtomicLong(1);
		Map<Long, TestItem> expectedItems = LocalDate.of(2020, 1, 1)
			.datesUntil(LocalDate.of(2020, 6, 1))
			.map(date -> TestItem.builder().id(id.getAndIncrement()).defaultDate(date).build())
			.map(this.repository::save)
			.collect(Collectors.toMap(TestItem::getId, Function.identity()));
		List<TestItem> foundItems = this.repository.findAllByDefaultDateBetweenSql(LocalDate.of(2020, 1, 1),
				LocalDate.of(2020, 6, 1));
		assertThat(foundItems).hasSize(expectedItems.size());
		for (TestItem foundItem : foundItems) {
			TestItem expectedItem = expectedItems.remove(foundItem.getId());
			assertThat(expectedItem).isNotNull();
			assertThat(expectedItem.getDefaultDate()).isEqualTo(foundItem.getDefaultDate());
		}
		assertThat(expectedItems).isEmpty();
	}

	@Test
	void findByCustomTimeSql() {
		TestItem expectedItem = TestItem.builder().id(1L).customTime(LocalTime.of(15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByCustomTimeSql(LocalTime.of(15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomTime()).isEqualTo(expectedItem.getCustomTime());
	}

	@Test
	void findByCustomDateSql() {
		TestItem expectedItem = TestItem.builder().id(1L).customDate(LocalDate.of(2020, 1, 1)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByCustomDateSql(LocalDate.of(2020, 1, 1)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomDate()).isEqualTo(expectedItem.getCustomDate());
	}

	@Test
	void findByCustomDateTimeSql() {
		TestItem expectedItem = TestItem.builder().id(1L).customDateTime(LocalDateTime.of(2020, 1, 1, 15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByCustomDateTimeSql(LocalDateTime.of(2020, 1, 1, 15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomDateTime()).isEqualTo(expectedItem.getCustomDateTime());
	}

	@Test
	void findPastItems() {
		this.repository.save(TestItem.builder().id(1L).defaultDateTime(LocalDateTime.now().minusDays(1)).build());
		this.repository.save(TestItem.builder().id(2L).defaultDateTime(LocalDateTime.now().minusDays(2)).build());
		this.repository.save(TestItem.builder().id(3L).defaultDateTime(LocalDateTime.now().minusDays(3)).build());
		this.repository.save(TestItem.builder().id(4L).defaultDateTime(LocalDateTime.now().plusDays(1)).build());
		List<TestItem> foundItems = this.repository.findPastItems();
		assertThat(foundItems).hasSize(3);
		assertThat(foundItems).map(TestItem::getId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void findPastItemsInverted() {
		this.repository.save(TestItem.builder().id(1L).defaultDateTime(LocalDateTime.now().minusDays(1)).build());
		this.repository.save(TestItem.builder().id(2L).defaultDateTime(LocalDateTime.now().minusDays(2)).build());
		this.repository.save(TestItem.builder().id(3L).defaultDateTime(LocalDateTime.now().minusDays(3)).build());
		this.repository.save(TestItem.builder().id(4L).defaultDateTime(LocalDateTime.now().plusDays(1)).build());
		List<TestItem> foundItems = this.repository.findPastItemsInverted();
		assertThat(foundItems).hasSize(3);
		assertThat(foundItems).map(TestItem::getId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void findItemsCitiesLengthGreaterThan() {
		this.repository.save(TestItem.builder().id(1L).cities(List.of("London", "Berlin", "Paris")).build());
		this.repository
			.save(TestItem.builder().id(2L).cities(List.of("Boston", "Warsaw", "Madrid", "Barcelona")).build());
		this.repository.save(TestItem.builder().id(3L).cities(List.of("Dublin", "Amsterdam")).build());
		assertThat(this.repository.findItemsCitiesLengthGreaterThan(1)).map(TestItem::getId)
			.containsExactly(1L, 2L, 3L);
		assertThat(this.repository.findItemsCitiesLengthGreaterThan(2)).map(TestItem::getId).containsExactly(1L, 2L);
		assertThat(this.repository.findItemsCitiesLengthGreaterThan(3)).map(TestItem::getId).containsExactly(2L);
	}

	@Test
	void findItemsCitiesLengthGreaterThanInverted() {
		this.repository.save(TestItem.builder().id(1L).cities(List.of("London", "Berlin", "Paris")).build());
		this.repository
			.save(TestItem.builder().id(2L).cities(List.of("Boston", "Warsaw", "Madrid", "Barcelona")).build());
		this.repository.save(TestItem.builder().id(3L).cities(List.of("Dublin", "Amsterdam")).build());
		assertThat(this.repository.findItemsCitiesLengthGreaterThanInverted(1)).map(TestItem::getId)
			.containsExactly(1L, 2L, 3L);
		assertThat(this.repository.findItemsCitiesLengthGreaterThanInverted(2)).map(TestItem::getId)
			.containsExactly(1L, 2L);
		assertThat(this.repository.findItemsCitiesLengthGreaterThanInverted(3)).map(TestItem::getId)
			.containsExactly(2L);
	}

}
