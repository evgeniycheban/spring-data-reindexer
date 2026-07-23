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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.reindexer.repository.item.TestItemSimpleTypesRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItemSimpleTypes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReindexerRepository}'s simple types support e.g., {@code UUID},
 * {@code BigInteger}, {@code BigDecimal} etc.
 *
 * @author Evgeniy Cheban
 */
class ReindexerSimpleTypesRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemSimpleTypesRepository repository;

	@Test
	void findItemWithUuidById() {
		BigInteger id = new BigInteger("123456789012345678910");
		UUID uuid = UUID.randomUUID();
		TestItemSimpleTypes item = TestItemSimpleTypes.builder().id(id).uuid(uuid).build();
		this.repository.save(item);
		TestItemSimpleTypes found = this.repository.findById(id).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(id);
		assertThat(found.getUuid()).isEqualTo(uuid);
	}

	@Test
	void findItemWithBigIntegerById() {
		BigInteger id = new BigInteger("123456789012345678910");
		BigInteger bigInteger = new BigInteger("1234567890123456789");
		TestItemSimpleTypes item = TestItemSimpleTypes.builder().id(id).bigInteger(bigInteger).build();
		this.repository.save(item);
		TestItemSimpleTypes found = this.repository.findById(id).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(id);
		assertThat(found.getBigInteger()).isEqualByComparingTo(bigInteger);
	}

	@Test
	void findItemWithBigDecimalById() {
		BigInteger id = new BigInteger("123456789012345678910");
		BigDecimal bigDecimal = new BigDecimal("1234567890123456789.123456789");
		TestItemSimpleTypes item = TestItemSimpleTypes.builder().id(id).bigDecimal(bigDecimal).build();
		this.repository.save(item);
		TestItemSimpleTypes found = this.repository.findById(id).orElse(null);
		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(id);
		assertThat(found.getBigDecimal()).isEqualByComparingTo(bigDecimal);
	}

	@Test
	void findAllByBigIntegerGreaterThan() {
		BigInteger id1 = new BigInteger("123456789012345678910");
		BigInteger id2 = new BigInteger("123456789012345678911");
		BigInteger id3 = new BigInteger("123456789012345678912");
		BigInteger bigInteger1 = new BigInteger("12345678901234567");
		BigInteger bigInteger2 = new BigInteger("123456789012345678");
		BigInteger bigInteger3 = new BigInteger("1234567890123456789");
		TestItemSimpleTypes item1 = TestItemSimpleTypes.builder().id(id1).bigInteger(bigInteger1).build();
		TestItemSimpleTypes item2 = TestItemSimpleTypes.builder().id(id2).bigInteger(bigInteger2).build();
		TestItemSimpleTypes item3 = TestItemSimpleTypes.builder().id(id3).bigInteger(bigInteger3).build();
		this.repository.saveAll(List.of(item1, item2, item3));
		List<TestItemSimpleTypes> actual = this.repository.findAllByBigIntegerGreaterThan(bigInteger1);
		assertThat(actual).hasSize(2);
		assertThat(actual).element(0).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(id2);
			assertThat(it.getBigInteger()).isEqualByComparingTo(bigInteger2);
		});
		assertThat(actual).element(1).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(id3);
			assertThat(it.getBigInteger()).isEqualByComparingTo(bigInteger3);
		});
	}

	@Test
	void findAllByBigDecimalGreaterThan() {
		BigInteger id1 = new BigInteger("123456789012345678910");
		BigInteger id2 = new BigInteger("123456789012345678911");
		BigInteger id3 = new BigInteger("123456789012345678912");
		BigDecimal bigDecimal1 = new BigDecimal("1234567890123456789.1234567");
		BigDecimal bigDecimal2 = new BigDecimal("1234567890123456789.12345678");
		BigDecimal bigDecimal3 = new BigDecimal("1234567890123456789.123456789");
		TestItemSimpleTypes item1 = TestItemSimpleTypes.builder().id(id1).bigDecimal(bigDecimal1).build();
		TestItemSimpleTypes item2 = TestItemSimpleTypes.builder().id(id2).bigDecimal(bigDecimal2).build();
		TestItemSimpleTypes item3 = TestItemSimpleTypes.builder().id(id3).bigDecimal(bigDecimal3).build();
		this.repository.saveAll(List.of(item1, item2, item3));
		List<TestItemSimpleTypes> actual = this.repository.findAllByBigDecimalGreaterThan(bigDecimal1);
		assertThat(actual).hasSize(2);
		assertThat(actual).element(0).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(id2);
			assertThat(it.getBigDecimal()).isEqualByComparingTo(bigDecimal2);
		});
		assertThat(actual).element(1).satisfies(it -> {
			assertThat(it.getId()).isEqualTo(id3);
			assertThat(it.getBigDecimal()).isEqualByComparingTo(bigDecimal3);
		});
	}

}
