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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.dto.TestEnum;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}'s derived query methods.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
class ReindexerDerivedQueryRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Test
	void findByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findByName("TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findByNameAndValue() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findByNameAndValue("TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findByNameOrValue() {
		TestItem testItem = this.repository.save(new TestItem(1L, null, "TestValue"));
		TestItem item = this.repository.findByNameOrValue("TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	// gh-96
	@Test
	void findByNameOrValueWhenFirstOrMatches() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findByNameOrValue("TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	// gh-96
	@Test
	void findByNameOrValueNot() {
		TestItem testItem = this.repository.save(new TestItem(1L, null, "TestValue"));
		TestItem item = this.repository.findByNameOrValueNot("TestName", "TestValueNot").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	// gh-96
	@Test
	void findByNameOrValueNotWhenFirstOrMatches() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findByNameOrValueNot("TestName", "TestValueNot").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findByTestEnumString() {
		TestItem testItem = this.repository
			.save(new TestItem(1L, "TestName", "TestValue", TestEnum.TEST_CONSTANT_1, null));
		TestItem item = this.repository.findByTestEnumString(TestEnum.TEST_CONSTANT_1).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
		assertEquals(testItem.getTestEnumString(), item.getTestEnumString());
		assertEquals(testItem.getTestEnumOrdinal(), item.getTestEnumOrdinal());
	}

	@Test
	void findByTestEnumOrdinal() {
		TestItem testItem = this.repository
			.save(new TestItem(1L, "TestName", "TestValue", null, TestEnum.TEST_CONSTANT_1));
		TestItem item = this.repository.findByTestEnumOrdinal(TestEnum.TEST_CONSTANT_1).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
		assertEquals(testItem.getTestEnumString(), item.getTestEnumString());
		assertEquals(testItem.getTestEnumOrdinal(), item.getTestEnumOrdinal());
	}

	@Test
	void findIteratorByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestValue", null));
		try (ResultIterator<TestItem> it = this.repository.findIteratorByName("TestValue")) {
			assertTrue(it.hasNext());
			TestItem item = it.next();
			assertEquals(testItem.getId(), item.getId());
			assertEquals(testItem.getName(), item.getName());
			assertEquals(testItem.getValue(), item.getValue());
			assertFalse(it.hasNext());
		}
	}

	@Test
	void getByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.getByName("TestName");
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	void findByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository
			.findByIdIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
	}

	@Test
	void findByIdInArray() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository
			.findByIdIn(expectedItems.stream().mapToLong(TestItem::getId).toArray());
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findByIdNotIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository
			.findByIdNotIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(0, foundItems.size());
	}

	@Test
	void existsByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertTrue(this.repository.existsByName(testItem.getName()));
	}

	@Test
	void countByValue() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue1"));
		assertEquals(2, this.repository.countByValue("TestValue"));
	}

	@Test
	void findAllSortedByIdInAscOrder() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAll(Sort.by(Direction.ASC, "id"));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < expectedItems.size(); i++) {
			assertEquals(expectedItems.get(i), foundItems.get(i));
		}
	}

	@Test
	void findAllSortedByIdInDescOrder() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAll(Sort.by(Direction.DESC, "id"));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < expectedItems.size(); i++) {
			assertEquals(expectedItems.get(i), foundItems.get(foundItems.size() - 1 - i));
		}
	}

	@Test
	void findByEnumStringIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository
				.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, TestEnum.values()[i], null)));
		}
		List<TestItem> foundItems = this.repository
			.findByTestEnumStringIn(expectedItems.stream().map(TestItem::getTestEnumString).toList());
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findByEnumStringInArray() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository
				.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, TestEnum.values()[i], null)));
		}
		List<TestItem> foundItems = this.repository
			.findByTestEnumStringIn(expectedItems.stream().map(TestItem::getTestEnumString).toArray(TestEnum[]::new));
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findByEnumOrdinalIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository
				.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, null, TestEnum.values()[i])));
		}
		List<TestItem> foundItems = this.repository
			.findByTestEnumOrdinalIn(expectedItems.stream().map(TestItem::getTestEnumOrdinal).toList());
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	void findByEnumOrdinalInArray() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository
				.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, null, TestEnum.values()[i])));
		}
		List<TestItem> foundItems = this.repository
			.findByTestEnumOrdinalIn(expectedItems.stream().map(TestItem::getTestEnumOrdinal).toArray(TestEnum[]::new));
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	void deleteByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertEquals(1, this.repository.count());
		this.repository.deleteByName(testItem.getName());
		assertEquals(0, this.repository.count());
	}

	@Test
	void findAllByLimit() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAllBy(Limit.of(10));
		for (TestItem item : foundItems) {
			assertTrue(expectedItems.remove(item));
		}
		assertEquals(90, expectedItems.size());
	}

	@Test
	void findFirstByOrderByIdAsc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findFirstByOrderByIdAsc().orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItems.get(0), foundItem);
	}

	@Test
	void findFirstByOrderByIdDesc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findFirstByOrderByIdDesc().orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItems.get(expectedItems.size() - 1), foundItem);
	}

	@Test
	void findTopByOrderByIdAsc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findTopByOrderByIdAsc().orElse(null);
		assertEquals(expectedItems.get(0), foundItem);
	}

	@Test
	void findTopByOrderByIdDesc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findTopByOrderByIdDesc().orElse(null);
		assertEquals(expectedItems.get(expectedItems.size() - 1), foundItem);
	}

	@Test
	void findTop10ByOrderByIdAsc() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findTop10ByOrderByIdAsc();
		for (TestItem item : foundItems) {
			assertTrue(expectedItems.remove(item));
		}
		assertEquals(90, expectedItems.size());
	}

	@Test
	void findTop10ByOrderByIdDesc() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findTop10ByOrderByIdDesc();
		for (TestItem item : foundItems) {
			assertTrue(expectedItems.remove(item));
		}
		assertEquals(90, expectedItems.size());
	}

	@Test
	void findAllByIdBetween() {
		for (long i = 0; i < 100; i++) {
			this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i));
		}
		List<TestItem> foundItems = this.repository.findAllByIdBetween(80L, 90L);
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsExactly(80L, 81L, 82L, 83L, 84L, 85L, 86L,
				87L, 88L, 89L, 90L);
	}

	@Test
	void findByActiveIsTrue() {
		this.repository.save(new TestItem(1L, true));
		this.repository.save(new TestItem(2L, true));
		this.repository.save(new TestItem(3L, false));
		this.repository.save(new TestItem(4L, false));
		List<TestItem> foundItems = this.repository.findByActiveIsTrue();
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	void findByActiveIsFalse() {
		this.repository.save(new TestItem(1L, true));
		this.repository.save(new TestItem(2L, true));
		this.repository.save(new TestItem(3L, false));
		this.repository.save(new TestItem(4L, false));
		List<TestItem> foundItems = this.repository.findByActiveIsFalse();
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L, 4L);
	}

	@Test
	void findAllByNameLike() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameLike("%LIMITED");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	void findAllByNameNotLike() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameNotLike("%LIMITED");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	void findAllByCitiesContaining() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1", List.of("City1", "City2")));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue2", List.of("City1", "City3")));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3", List.of("City2", "City3")));
		List<TestItem> foundItems = this.repository.findAllByCitiesContaining("City1");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	void findAllByCitiesNotContaining() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1", List.of("City1", "City2")));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue2", List.of("City1", "City3")));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3", List.of("City2", "City3")));
		List<TestItem> foundItems = this.repository.findAllByCitiesNotContaining("City1");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	void findAllByNameContaining() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameContaining("LIMIT");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	void findAllByNameNotContaining() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameNotContaining("LIMIT");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	void findAllByNameStartingWith() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameStartingWith("Test");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	void findAllByNameEndingWith() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameEndingWith("ED");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	void finByNameIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		Optional<TestItem> foundItem = this.repository.findByNameIgnoreCase("testname");
		assertTrue(foundItem.isPresent());
	}

	@Test
	void findByNameNotIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		List<TestItem> foundItem = this.repository.findByNameNotIgnoreCase("testname2");
		assertThat(foundItem.stream().map(TestItem::getId).toList()).containsOnly(1L);
	}

	@Test
	void findByIdAndNameIgnoreCaseAndValueAllIgnoreCase() {
		this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		Optional<TestItem> foundItem = this.repository.findByIdAndNameIgnoreCaseAndValueAllIgnoreCase(1L, "testname",
				"testvalue");
		assertTrue(foundItem.isPresent());
	}

}
