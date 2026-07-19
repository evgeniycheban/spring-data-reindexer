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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReindexerRepository}'s JSR-310 types support.
 *
 * @author Evgeniy Cheban
 */
class ReindexerDateTimeRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemReindexerRepository repository;

	@Test
	void saveWhenJsr310TypesThenConverted() {
		TestItem expectedItem = TestItem.builder()
			.id(1L)
			.customDate(LocalDate.of(2020, 1, 1))
			.customTime(LocalTime.of(15, 30))
			.customDateTime(LocalDateTime.of(2020, 1, 1, 15, 30))
			.defaultDate(LocalDate.of(2020, 1, 1))
			.defaultTime(LocalTime.of(15, 30))
			.defaultDateTime(LocalDateTime.of(2020, 1, 1, 15, 30))
			.build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findById(expectedItem.getId()).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomDate()).isEqualTo(expectedItem.getCustomDate());
		assertThat(foundItem.getCustomTime()).isEqualTo(expectedItem.getCustomTime());
		assertThat(foundItem.getCustomDateTime()).isEqualTo(expectedItem.getCustomDateTime());
		assertThat(foundItem.getDefaultDate()).isEqualTo(expectedItem.getDefaultDate());
		assertThat(foundItem.getDefaultTime()).isEqualTo(expectedItem.getDefaultTime());
		assertThat(foundItem.getDefaultDateTime()).isEqualTo(expectedItem.getDefaultDateTime());
	}

	@Test
	void findByDefaultTime() {
		TestItem expectedItem = TestItem.builder().id(1L).defaultTime(LocalTime.of(15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByDefaultTime(LocalTime.of(15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getDefaultTime()).isEqualTo(expectedItem.getDefaultTime());
	}

	@Test
	void findByDefaultDate() {
		TestItem expectedItem = TestItem.builder().id(1L).defaultDate(LocalDate.of(2020, 1, 1)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByDefaultDate(LocalDate.of(2020, 1, 1)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getDefaultDate()).isEqualTo(expectedItem.getDefaultDate());
	}

	@Test
	void findByDefaultDateTime() {
		TestItem expectedItem = TestItem.builder().id(1L).defaultDateTime(LocalDateTime.of(2020, 1, 1, 15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByDefaultDateTime(LocalDateTime.of(2020, 1, 1, 15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getDefaultDateTime()).isEqualTo(expectedItem.getDefaultDateTime());
	}

	@Test
	void findAllByDefaultDateBetween() {
		AtomicLong id = new AtomicLong(1);
		Map<Long, TestItem> expectedItems = LocalDate.of(2020, 1, 1)
			.datesUntil(LocalDate.of(2020, 6, 1))
			.map(date -> TestItem.builder().id(id.getAndIncrement()).defaultDate(date).build())
			.map(this.repository::save)
			.collect(Collectors.toMap(TestItem::getId, Function.identity()));
		List<TestItem> foundItems = this.repository.findAllByDefaultDateBetween(LocalDate.of(2020, 1, 1),
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
	void findByCustomTime() {
		TestItem expectedItem = TestItem.builder().id(1L).customTime(LocalTime.of(15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByCustomTime(LocalTime.of(15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomTime()).isEqualTo(expectedItem.getCustomTime());
	}

	@Test
	void findByCustomDate() {
		TestItem expectedItem = TestItem.builder().id(1L).customDate(LocalDate.of(2020, 1, 1)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByCustomDate(LocalDate.of(2020, 1, 1)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomDate()).isEqualTo(expectedItem.getCustomDate());
	}

	@Test
	void findByCustomDateTime() {
		TestItem expectedItem = TestItem.builder().id(1L).customDateTime(LocalDateTime.of(2020, 1, 1, 15, 30)).build();
		this.repository.save(expectedItem);
		TestItem foundItem = this.repository.findByCustomDateTime(LocalDateTime.of(2020, 1, 1, 15, 30)).orElse(null);
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getCustomDateTime()).isEqualTo(expectedItem.getCustomDateTime());
	}

}
