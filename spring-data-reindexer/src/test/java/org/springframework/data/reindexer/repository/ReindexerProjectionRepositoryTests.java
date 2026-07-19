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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.TestJoinedItemRepository;
import org.springframework.data.reindexer.repository.item.dto.Place;
import org.springframework.data.reindexer.repository.item.dto.Price;
import org.springframework.data.reindexer.repository.item.dto.TestItemDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameRecord;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueJoinedItemProjection;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueProjection;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueRecord;
import org.springframework.data.reindexer.repository.item.dto.TestItemPreferredConstructorDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemPreferredConstructorRecord;
import org.springframework.data.reindexer.repository.item.dto.TestItemProjection;
import org.springframework.data.reindexer.repository.item.dto.TestItemProjectionWithJoinedItems;
import org.springframework.data.reindexer.repository.item.dto.TestItemRecord;
import org.springframework.data.reindexer.repository.item.dto.TestJoinedItemProjection;
import org.springframework.data.reindexer.repository.item.dto.TestNestedItem;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.item.entity.TestJoinedItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}'s projection methods.
 *
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
class ReindexerProjectionRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Autowired
	TestJoinedItemRepository joinedItemRepository;

	@Test
	void findProjectionByNameWithJoinedItems() {
		TestJoinedItem nestedJoinedItem = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName1"));
		TestJoinedItem joinedItem = this.joinedItemRepository
			.save(new TestJoinedItem(2L, nestedJoinedItem.getId(), "TestName2"));
		List<TestJoinedItem> expectedJoinedItems = new ArrayList<>();
		expectedJoinedItems
			.add(this.joinedItemRepository.save(new TestJoinedItem(3L, nestedJoinedItem.getId(), "TestName3")));
		expectedJoinedItems
			.add(this.joinedItemRepository.save(new TestJoinedItem(4L, nestedJoinedItem.getId(), "TestName4")));
		expectedJoinedItems
			.add(this.joinedItemRepository.save(new TestJoinedItem(5L, nestedJoinedItem.getId(), "TestName5")));
		List<Long> joinedItemIds = expectedJoinedItems.stream().map(TestJoinedItem::getId).toList();
		TestNestedItem nestedItem = new TestNestedItem("TestNestedName", "TestNestedValue");
		TestItem expectedItem = this.repository.save(new TestItem(1L, nestedItem, joinedItem.getId(), joinedItemIds,
				"TestName", "TestValue", "2015-01-01", "2015-01-01T15:30"));
		TestItemProjectionWithJoinedItems foundItem = this.repository.findProjectionByName("TestName");
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getLocalDate()).isEqualTo(LocalDate.parse(expectedItem.getLocalDate()));
		assertThat(foundItem.getLocalDateTime()).isEqualTo(LocalDateTime.parse(expectedItem.getLocalDateTime()));
		assertThat(foundItem.getNestedItem()).isNotNull();
		assertThat(foundItem.getNestedItem().name()).isEqualTo(nestedItem.getName());
		assertThat(foundItem.getNestedItem().value()).isEqualTo(nestedItem.getValue());
		assertThat(foundItem.getJoinedItem().getId()).isEqualTo(joinedItem.getId());
		assertThat(foundItem.getJoinedItem().getName()).isEqualTo(joinedItem.getName());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem()).isNotNull();
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItemLazy()).isNotNull();
		assertThat(foundItem.getJoinedItem().getNestedJoinedItemLazy().getId()).isEqualTo(nestedJoinedItem.getId());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItemLazy().getName()).isEqualTo(nestedJoinedItem.getName());
		assertThat(foundItem.getJoinedItemLazy()).isNotNull();
		assertThat(foundItem.getJoinedItemLazy().getId()).isEqualTo(joinedItem.getId());
		assertThat(foundItem.getJoinedItemLazy().getName()).isEqualTo(joinedItem.getName());
		assertThat(foundItem.getJoinedItemLazy().getNestedJoinedItem()).isNotNull();
		assertThat(foundItem.getJoinedItemLazy().getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
		assertThat(foundItem.getJoinedItemLazy().getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
		assertThat(foundItem.getJoinedItemLazy().getNestedJoinedItemLazy()).isNotNull();
		assertThat(foundItem.getJoinedItemLazy().getNestedJoinedItemLazy().getId()).isEqualTo(nestedJoinedItem.getId());
		assertThat(foundItem.getJoinedItemLazy().getNestedJoinedItemLazy().getName())
			.isEqualTo(nestedJoinedItem.getName());
		assertThat(foundItem.getJoinedItems()).hasSize(expectedJoinedItems.size());
		int i = 0;
		for (TestJoinedItemProjection foundJoinedItem : foundItem.getJoinedItems()) {
			TestJoinedItem expectedJoinedItem = expectedJoinedItems.get(i++);
			assertThat(foundJoinedItem.getId()).isEqualTo(expectedJoinedItem.getId());
			assertThat(foundJoinedItem.getName()).isEqualTo(expectedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItem()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItemLazy()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItemLazy().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItemLazy().getName()).isEqualTo(nestedJoinedItem.getName());
		}
		assertThat(foundItem.getJoinedItemsReverseOrder()).hasSize(expectedJoinedItems.size());
		i = 0;
		for (TestJoinedItemProjection foundJoinedItem : foundItem.getJoinedItemsReverseOrder()) {
			TestJoinedItem expectedJoinedItem = expectedJoinedItems.get(expectedJoinedItems.size() - 1 - i++);
			assertThat(foundJoinedItem.getId()).isEqualTo(expectedJoinedItem.getId());
			assertThat(foundJoinedItem.getName()).isEqualTo(expectedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItem()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItemLazy()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItemLazy().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItemLazy().getName()).isEqualTo(nestedJoinedItem.getName());
		}
		assertThat(foundItem.getJoinedItemsRepository()).hasSize(expectedJoinedItems.size());
		i = 0;
		for (TestJoinedItemProjection foundJoinedItem : foundItem.getJoinedItemsRepository()) {
			TestJoinedItem expectedJoinedItem = expectedJoinedItems.get(i++);
			assertThat(foundJoinedItem.getId()).isEqualTo(expectedJoinedItem.getId());
			assertThat(foundJoinedItem.getName()).isEqualTo(expectedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItem()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
			assertThat(foundJoinedItem.getNestedJoinedItemLazy()).isNotNull();
			assertThat(foundJoinedItem.getNestedJoinedItemLazy().getId()).isEqualTo(nestedJoinedItem.getId());
			assertThat(foundJoinedItem.getNestedJoinedItemLazy().getName()).isEqualTo(nestedJoinedItem.getName());
		}
	}

	@Test
	void findTestItemDtoByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue", new Price(100.0),
				new Place("TestCountry", List.of("TestCity1", "TestCity2", "TestCity3")), List.of()));
		Optional<TestItemDto> foundItem = this.repository.findTestItemDTOByName("TestName");
		assertTrue(foundItem.isPresent());
		TestItemDto testItemDto = foundItem.get();
		assertEquals(testItem.getId(), testItemDto.getId());
		assertEquals(testItem.getName(), testItemDto.getName());
		assertEquals(testItem.getValue(), testItemDto.getValue());
		assertEquals(testItem.getName() + " " + testItem.getValue(), testItemDto.getNameValueExpression());
		assertEquals(testItem.getPrice().getValue(), testItemDto.getPrice());
		assertEquals("Country: " + testItem.getPlace().getCountry() + ", cities: " + testItem.getPlace().getCities(),
				testItemDto.getPlace());
		assertNotNull(testItemDto.getPlaces());
		assertEquals(0, testItemDto.getPlaces().size());
	}

	@Test
	void findTestItemDtoWhenPriceIsNull() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue", null,
				new Place("TestCountry", List.of("TestCity1", "TestCity2", "TestCity3")), List.of()));
		Optional<TestItemDto> foundItem = this.repository.findTestItemDTOByName("TestName");
		assertTrue(foundItem.isPresent());
		TestItemDto testItemDto = foundItem.get();
		assertEquals(testItem.getId(), testItemDto.getId());
		assertEquals(testItem.getName(), testItemDto.getName());
		assertEquals(testItem.getValue(), testItemDto.getValue());
		assertEquals(testItem.getName() + " " + testItem.getValue(), testItemDto.getNameValueExpression());
		assertEquals(0.0, testItemDto.getPrice());
		assertEquals("Country: " + testItem.getPlace().getCountry() + ", cities: " + testItem.getPlace().getCities(),
				testItemDto.getPlace());
		assertNotNull(testItemDto.getPlaces());
		assertEquals(0, testItemDto.getPlaces().size());
	}

	@Test
	void findTestItemDtoWithPlaces() {
		Place place1 = new Place("TestCountry1", List.of("TestCity1", "TestCity2", "TestCity3"));
		Place place2 = new Place("TestCountry2", List.of("TestCity4", "TestCity5", "TestCity6"));
		Place place3 = new Place("TestCountry2", List.of("TestCity7", "TestCity8", "TestCity9"));
		TestItem testItem = this.repository
			.save(new TestItem(1L, "TestName", "TestValue", new Price(100.0), place1, List.of(place2, place3)));
		Optional<TestItemDto> foundItem = this.repository.findTestItemDTOByName("TestName");
		assertTrue(foundItem.isPresent());
		TestItemDto testItemDto = foundItem.get();
		assertEquals(testItem.getId(), testItemDto.getId());
		assertEquals(testItem.getName(), testItemDto.getName());
		assertEquals(testItem.getValue(), testItemDto.getValue());
		assertEquals(testItem.getName() + " " + testItem.getValue(), testItemDto.getNameValueExpression());
		assertEquals(testItem.getPrice().getValue(), testItemDto.getPrice());
		assertEquals("Country: " + place1.getCountry() + ", cities: " + place1.getCities(), testItemDto.getPlace());
		assertNotNull(testItemDto.getPlaces());
		assertEquals(2, testItemDto.getPlaces().size());
		assertEquals("Country: " + place2.getCountry() + ", cities: " + place2.getCities(),
				testItemDto.getPlaces().get(0));
		assertEquals("Country: " + place3.getCountry() + ", cities: " + place3.getCities(),
				testItemDto.getPlaces().get(1));
	}

	@Test
	void findItemProjectionByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemProjection> foundItems = this.repository
			.findItemProjectionByIdIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	void findItemDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemDto> foundItems = this.repository
			.findItemDtoByIdIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	void findItemPreferredConstructorDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorDto> foundItems = this.repository.findItemPreferredConstructorDtoByIdIn(
				expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	void findItemRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository
			.findItemRecordByIdIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	void findItemPreferredConstructorRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorRecord> foundItems = this.repository.findItemPreferredConstructorRecordByIdIn(
				expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertNull(foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	void findDynamicItemProjectionByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemProjection> foundItems = this.repository.findByIdIn(
				expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()), TestItemProjection.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	void findDynamicItemDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemDto> foundItems = this.repository
			.findByIdIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()), TestItemDto.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	void findDynamicItemPreferredConstructorDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorDto> foundItems = this.repository.findByIdIn(
				expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()),
				TestItemPreferredConstructorDto.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	void findDynamicItemRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository
			.findByIdIn(expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()), TestItemRecord.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	void findDynamicItemPreferredConstructorRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorRecord> foundItems = this.repository.findByIdIn(
				expectedItems.stream().map(TestItem::getId).collect(Collectors.toList()),
				TestItemPreferredConstructorRecord.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertNull(foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	void findRecordWithUuidById() {
		long id = 1L;
		UUID uuid = UUID.randomUUID();
		TestItem item = TestItem.builder().id(id).uuid(uuid).build();
		this.repository.save(item);
		TestItemRecord found = this.repository.findById(id, TestItemRecord.class).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(id);
		assertThat(found.uuid()).isEqualTo(uuid.toString());
	}

	@Test
	void findDistinctNameRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName2", "TestValue3"));
		List<TestItemNameRecord> foundItems = this.repository.findDistinctNameRecordByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameRecord::name).toList()).containsOnly("TestName1", "TestName2");
	}

	@Test
	void findDistinctNameValueRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueRecord> foundItems = this.repository
			.findDistinctNameValueRecordByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueRecord::name).toList()).containsOnly("TestName1",
				"TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueRecord::value).toList()).containsOnly("TestValue2",
				"TestValue3");
	}

	@Test
	void findDistinctNameValueProjectionByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueProjection> foundItems = this.repository
			.findDistinctNameValueProjectionByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueProjection::getName).toList()).containsOnly("TestName1",
				"TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueProjection::getValue).toList()).containsOnly("TestValue2",
				"TestValue3");
	}

	@Test
	void findDistinctNameValueJoinedItemProjectionByIdIn() {
		TestJoinedItem joinedItem1 = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName1"));
		TestJoinedItem joinedItem2 = this.joinedItemRepository.save(new TestJoinedItem(2L, "TestName2"));
		this.repository.save(new TestItem(1L, null, joinedItem1.getId(), Collections.emptyList(), "TestName1",
				"TestValue2", null, null));
		this.repository.save(new TestItem(2L, null, joinedItem2.getId(), Collections.emptyList(), "TestName2",
				"TestValue3", null, null));
		this.repository.save(new TestItem(3L, null, joinedItem2.getId(), Collections.emptyList(), "TestName3",
				"TestValue3", null, null));
		List<TestItemNameValueJoinedItemProjection> foundItems = this.repository
			.findDistinctNameValueJoinedItemProjectionByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueJoinedItemProjection::getName).toList())
			.containsOnly("TestName1", "TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueJoinedItemProjection::getValue).toList())
			.containsOnly("TestValue2", "TestValue3");
		assertThat(foundItems.stream()
			.map(TestItemNameValueJoinedItemProjection::getJoinedItemLazy)
			.map(TestJoinedItem::getId)
			.toList()).containsOnly(1L, 2L);
	}

	@Test
	void findDistinctNameValueDtoByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueDto> foundItems = this.repository.findDistinctNameValueDtoByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueDto::getName).toList()).containsOnly("TestName1",
				"TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueDto::getValue).toList()).containsOnly("TestValue2",
				"TestValue3");
	}

	@Test
	void findDistinctDynamicProjectionRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueRecord> foundItems = this.repository.findDistinctByIdIn(List.of(1L, 2L, 3L),
				TestItemNameValueRecord.class);
		assertThat(foundItems.stream().map(TestItemNameValueRecord::name).toList()).containsOnly("TestName1",
				"TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueRecord::value).toList()).containsOnly("TestValue2",
				"TestValue3");
	}

	@Test
	void findDistinctDynamicProjectionInterfaceByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueProjection> foundItems = this.repository.findDistinctByIdIn(List.of(1L, 2L, 3L),
				TestItemNameValueProjection.class);
		assertThat(foundItems.stream().map(TestItemNameValueProjection::getName).toList()).containsOnly("TestName1",
				"TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueProjection::getValue).toList()).containsOnly("TestValue2",
				"TestValue3");
	}

	@Test
	void findDistinctDynamicProjectionClassByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueDto> foundItems = this.repository.findDistinctByIdIn(List.of(1L, 2L, 3L),
				TestItemNameValueDto.class);
		assertThat(foundItems.stream().map(TestItemNameValueDto::getName).toList()).containsOnly("TestName1",
				"TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueDto::getValue).toList()).containsOnly("TestValue2",
				"TestValue3");
	}

	// gh-86
	@Test
	void streamDistinctNameValueRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		try (Stream<TestItemNameValueRecord> stream = this.repository
			.streamDistinctNameValueRecordByIdIn(List.of(1L, 2L, 3L))) {
			List<TestItemNameValueRecord> foundItems = stream.toList();
			assertThat(foundItems.stream().map(TestItemNameValueRecord::name).toList()).containsOnly("TestName1",
					"TestName2");
			assertThat(foundItems.stream().map(TestItemNameValueRecord::value).toList()).containsOnly("TestValue2",
					"TestValue3");
		}
	}

	// gh-86
	@Test
	void streamDistinctNameValueRecordByIdInWhenEmpty() {
		try (Stream<TestItemNameValueRecord> stream = this.repository
			.streamDistinctNameValueRecordByIdIn(List.of(1L, 2L, 3L))) {
			assertThat(stream).isEmpty();
		}
	}

	@Test
	void findAllItemProjectionByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemProjection> foundItems = this.repository.findAllItemProjectionByIdIn(
				expectedItems.stream().map(TestItem::getId).toList(), Sort.by(Sort.Direction.DESC, "id"));
		assertThat(foundItems).hasSameSizeAs(expectedItems);
		for (int i = 0; i < foundItems.size(); i++) {
			TestItemProjection foundItem = foundItems.get(i);
			TestItem expectedItem = expectedItems.get(expectedItems.size() - 1 - i);
			assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
			assertThat(foundItem.getName()).isEqualTo(expectedItem.getName());
		}
	}

	@Test
	void findAllItemDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemDto> foundItems = this.repository.findAllItemDtoByIdIn(
				expectedItems.stream().map(TestItem::getId).toList(), Sort.by(Sort.Direction.ASC, "name"));
		assertThat(foundItems).hasSameSizeAs(expectedItems);
		for (int i = 0; i < foundItems.size(); i++) {
			TestItemDto foundItem = foundItems.get(i);
			TestItem expectedItem = expectedItems.get(expectedItems.size() - 1 - i);
			assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
			assertThat(foundItem.getName()).isEqualTo(expectedItem.getName());
		}
	}

	@Test
	void findAllItemRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository
			.findAllItemRecordByIdIn(expectedItems.stream().map(TestItem::getId).toList());
		assertThat(foundItems).hasSameSizeAs(expectedItems);
		for (int i = 0; i < foundItems.size(); i++) {
			TestItemRecord foundItem = foundItems.get(i);
			TestItem expectedItem = expectedItems.get(i);
			assertThat(foundItem.id()).isEqualTo(expectedItem.getId());
			assertThat(foundItem.name()).isEqualTo(expectedItem.getName());
		}
	}

	@Test
	void findAllItemRecordByIdNotIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository
			.findAllItemRecordByIdNotIn(expectedItems.stream().map(TestItem::getId).toList());
		assertThat(foundItems).isEmpty();
	}

}
