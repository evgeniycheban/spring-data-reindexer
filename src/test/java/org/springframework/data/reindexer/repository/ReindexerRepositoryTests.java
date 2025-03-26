/*
 * Copyright 2022 evgeniycheban
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.reindexer.repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.rt.restream.reindexer.EnumType;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.annotations.Enumerated;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.annotations.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.reindexer.ReindexerTransactionManager;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.data.reindexer.repository.config.ReindexerConfigurationSupport;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}.
 *
 * @author Evgeniy Cheban
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@Testcontainers
class ReindexerRepositoryTests {

	private static final int REST_API_PORT = 9088;

	private static final int RPC_PORT = 6534;

	private static final String DATABASE_NAME = "test_items";

	private static final String NAMESPACE_NAME = "items";

	@Container
	static GenericContainer<?> reindexer = new GenericContainer<>(DockerImageName.parse("reindexer/reindexer"))
			.withExposedPorts(REST_API_PORT, RPC_PORT);

	@Autowired
	TestItemReindexerRepository repository;

	@Autowired
	TestJoinedItemRepository joinedItemRepository;

	@Autowired
	TestItemTransactionalService service;

	@BeforeAll
	static void beforeAll() throws Exception {
		CreateDatabase createDatabase = new CreateDatabase();
		createDatabase.setName(DATABASE_NAME);
		request(HttpPost.METHOD_NAME, "/db", createDatabase);
	}

	@AfterEach
	void tearDown() throws Exception {
		request(HttpDelete.METHOD_NAME, "/db/" + DATABASE_NAME + "/namespaces/" + NAMESPACE_NAME + "/truncate", null);
	}

	private static void request(String method, String path, Object body) throws IOException {
		String url = "http://localhost:" + reindexer.getMappedPort(REST_API_PORT) + "/api/v1" + path;
		Gson gson = new GsonBuilder()
				.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.create();
		String json = gson.toJson(body);
		HttpUriRequest request = RequestBuilder.create(method)
				.setUri(url)
				.setEntity(new StringEntity(json))
				.build();
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			client.execute(request);
		}
	}

	@Test
	public void findByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findByName("TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findByNameAndValue() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findByNameAndValue("TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findByNameOrValue() {
		TestItem testItem = this.repository.save(new TestItem(1L, null, "TestValue"));
		TestItem item = this.repository.findByNameOrValue("TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findByTestEnumString() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue", TestEnum.TEST_CONSTANT_1, null));
		TestItem item = this.repository.findByTestEnumString(TestEnum.TEST_CONSTANT_1).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
		assertEquals(testItem.getTestEnumString(), item.getTestEnumString());
		assertEquals(testItem.getTestEnumOrdinal(), item.getTestEnumOrdinal());
	}

	@Test
	public void findByTestEnumOrdinal() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue", null, TestEnum.TEST_CONSTANT_1));
		TestItem item = this.repository.findByTestEnumOrdinal(TestEnum.TEST_CONSTANT_1).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
		assertEquals(testItem.getTestEnumString(), item.getTestEnumString());
		assertEquals(testItem.getTestEnumOrdinal(), item.getTestEnumOrdinal());
	}

	@Test
	public void findIteratorByName() {
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
	public void findIteratorSqlByName() {
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
	public void findIteratorSqlByNameParam() {
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
	public void getByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.getByName("TestName");
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void getOneSqlByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.getOneSqlByName("TestName");
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void getOneSqlByNameParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.getOneSqlByNameParam("TestName");
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findOneSqlByName("TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByNameParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", null));
		TestItem item = this.repository.findOneSqlByNameParam("TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByNameManyParameters() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByNameAndValueManyParams(null, null,
				null, null, null, null, null, null, null, null, "TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByNameOrValue() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValue(1L, "TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByNameOrValueParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValueParam(1L, "TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlSpelByItemIdAndNameAndValueParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlSpelByItemIdAndNameAndValueParam(testItem).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlSpelByIdAndNameAndValueParam() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlSpelByIdAndNameAndValueParam(1L, "TestName", "TestValue").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByIdAndNameAndValueAnyParameterOrder() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValue("TestValue", 1L, "TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void findOneSqlByIdAndNameAndValueParamAnyParameterOrder() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findOneSqlByIdAndNameAndValueParam("TestValue", 1L, "TestName").orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void updateNameSql() {
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
	public void updateNameSqlParam() {
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
	public void save() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertNotNull(testItem);
		TestItem item = this.repository.findById(1L).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void saveTransactional() {
		TestItem testItem = this.service.save(new TestItem(1L, "TestName", "TestValue"));
		assertNotNull(testItem);
		TestItem item = this.repository.findById(1L).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void saveTransactionalExceptionally() {
		assertThrows(IllegalStateException.class,
				() -> this.service.saveExceptionally(new TestItem(1L, "TestName", "TestValue")));
		assertFalse(this.repository.existsById(1L));
	}

	@Test
	public void saveAll() {
		List<TestItem> items = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			items.add(new TestItem(i, "TestName" + i, "TestValue" + i));
		}
		Map<Long, TestItem> expectedItems = this.repository.saveAll(items).stream()
				.collect(Collectors.toMap(TestItem::getId, Function.identity()));
		assertEquals(items.size(), expectedItems.size());
		for (TestItem actual : this.repository.findAll()) {
			TestItem expected = expectedItems.remove(actual.getId());
			assertNotNull(expected);
			assertEquals(expected.getId(), actual.getId());
			assertEquals(expected.getName(), actual.getName());
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findById() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.findById(1L).orElse(null);
		assertNotNull(item);
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void existsById() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertTrue(this.repository.existsById(testItem.getId()));
	}

	@Test
	public void findAll() {
		Map<Long, TestItem> expectedItems = new HashMap<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.put(i, this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		for (TestItem actual : this.repository.findAll()) {
			TestItem expected = expectedItems.remove(actual.getId());
			assertNotNull(expected);
			assertEquals(expected.getId(), actual.getId());
			assertEquals(expected.getName(), actual.getName());
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findAllListSql() {
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
	public void findAllSetSql() {
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
	public void findAllStreamSql() {
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
	public void findAllById() {
		Map<Long, TestItem> expectedItems = new HashMap<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.put(i, this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<Long> ids = expectedItems.values().stream().map(TestItem::getId).collect(Collectors.toList());
		for (TestItem actual : this.repository.findAllById(ids)) {
			TestItem expected = expectedItems.remove(actual.getId());
			assertNotNull(expected);
			assertEquals(expected.getId(), actual.getId());
			assertEquals(expected.getName(), actual.getName());
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void queryGetOneById() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItem item = this.repository.query().where("id", Condition.EQ, 1L).getOne();
		assertEquals(testItem.getId(), item.getId());
		assertEquals(testItem.getName(), item.getName());
		assertEquals(testItem.getValue(), item.getValue());
	}

	@Test
	public void count() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		assertEquals(expectedItems.size(), this.repository.count());
	}

	@Test
	public void deleteById() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertTrue(this.repository.existsById(testItem.getId()));
		this.repository.deleteById(testItem.getId());
		assertFalse(this.repository.existsById(testItem.getId()));
	}

	@Test
	public void delete() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertTrue(this.repository.existsById(testItem.getId()));
		this.repository.delete(testItem);
		assertFalse(this.repository.existsById(testItem.getId()));
	}

	@Test
	public void deleteAllById() {
		List<Long> ids = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			TestItem testItem = this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i));
			ids.add(testItem.getId());
		}
		assertEquals(ids.size(), this.repository.count());
		this.repository.deleteAllById(ids);
		assertEquals(0, this.repository.count());
	}

	@Test
	public void deleteAllEntities() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		assertEquals(expectedItems.size(), this.repository.count());
		this.repository.deleteAll(expectedItems);
		assertEquals(0, this.repository.count());
	}

	@Test
	public void deleteAll() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		assertEquals(expectedItems.size(), this.repository.count());
		this.repository.deleteAll();
		assertEquals(0, this.repository.count());
	}

	@Test
	public void findByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
	}

	@Test
	public void findItemProjectionByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemProjection> foundItems = this.repository.findItemProjectionByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	public void findItemDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemDto> foundItems = this.repository.findItemDtoByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	public void findItemPreferredConstructorDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorDto> foundItems = this.repository.findItemPreferredConstructorDtoByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	public void findItemRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository.findItemRecordByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	public void findItemPreferredConstructorRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorRecord> foundItems = this.repository.findItemPreferredConstructorRecordByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertNull(foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	public void findDynamicItemProjectionByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemProjection> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()), TestItemProjection.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	public void findDynamicItemDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemDto> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()), TestItemDto.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	public void findDynamicItemPreferredConstructorDtoDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorDto> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()), TestItemPreferredConstructorDto.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).getId());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).getName());
		}
	}

	@Test
	public void findDynamicItemRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()), TestItemRecord.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertEquals(expectedItems.get(i).getId(), foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	public void findDynamicItemPreferredConstructorRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemPreferredConstructorRecord> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()), TestItemPreferredConstructorRecord.class);
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < foundItems.size(); i++) {
			assertNull(foundItems.get(i).id());
			assertEquals(expectedItems.get(i).getName(), foundItems.get(i).name());
		}
	}

	@Test
	public void findByIdInArray() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findByIdIn(expectedItems.stream()
				.mapToLong(TestItem::getId)
				.toArray());
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findByIdNotIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findByIdNotIn(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(0, foundItems.size());
	}

	@Test
	public void existsByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertTrue(this.repository.existsByName(testItem.getName()));
	}

	@Test
	public void countByValue() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue1"));
		assertEquals(2, this.repository.countByValue("TestValue"));
	}

	@Test
	public void findAllByIdInSortedByIdInAscOrder() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAllByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.toList(), Sort.by(Direction.ASC, "id"));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < expectedItems.size(); i++) {
			assertEquals(expectedItems.get(i), foundItems.get(i));
		}
	}

	@Test
	public void findAllByIdInSortedByIdInDescOrder() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findAllByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.toList(), Sort.by(Direction.DESC, "id"));
		assertEquals(expectedItems.size(), foundItems.size());
		for (int i = 0; i < expectedItems.size(); i++) {
			assertEquals(expectedItems.get(i), foundItems.get(foundItems.size() - 1 - i));
		}
	}

	@Test
	public void findByIdInPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = Pageable.ofSize(5);
		List<Long> expectedIds = expectedItems.stream()
				.map(TestItem::getId)
				.toList();
		long totalCount = this.repository.countByIdIn(expectedIds);
		do {
			List<TestItem> foundItems = this.repository.findByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = new PageImpl<>(foundItems, pageable, totalCount).nextPageable();
		} while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findPageByIdIn() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = Pageable.ofSize(5);
		List<Long> expectedIds = expectedItems.stream()
				.map(TestItem::getId)
				.toList();
		do {
			Page<TestItem> foundItems = this.repository.findPageByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		} while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findFirst2By() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst2By(PageRequest.of(0, 3, Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst2By(PageRequest.of(1, 3, Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	public void findFirst3By() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst3By(PageRequest.of(0, 2, Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst3By(PageRequest.of(1, 2, Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	public void findAllPageable() {
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
		} while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findAllSortedByIdInAscOrder() {
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
	public void findAllSortedByIdInDescOrder() {
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
	public void findByEnumStringIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, TestEnum.values()[i], null)));
		}
		List<TestItem> foundItems = this.repository.findByTestEnumStringIn(expectedItems.stream()
				.map(TestItem::getTestEnumString)
				.toList());
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findByEnumStringInArray() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, TestEnum.values()[i], null)));
		}
		List<TestItem> foundItems = this.repository.findByTestEnumStringIn(expectedItems.stream()
				.map(TestItem::getTestEnumString)
				.toArray(TestEnum[]::new));
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findByEnumOrdinalIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, null, TestEnum.values()[i])));
		}
		List<TestItem> foundItems = this.repository.findByTestEnumOrdinalIn(expectedItems.stream()
				.map(TestItem::getTestEnumOrdinal)
				.toList());
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findByEnumOrdinalInArray() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			expectedItems.add(this.repository.save(new TestItem((long) i, "TestName" + i, "TestValue" + i, null, TestEnum.values()[i])));
		}
		List<TestItem> foundItems = this.repository.findByTestEnumOrdinalIn(expectedItems.stream()
				.map(TestItem::getTestEnumOrdinal)
				.toArray(TestEnum[]::new));
		expectedItems.removeAll(foundItems);
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void deleteByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		assertEquals(1, this.repository.count());
		this.repository.deleteByName(testItem.getName());
		assertEquals(0, this.repository.count());
	}

	@Test
	public void saveAndDelete() {
		TestItem testItem = new TestItem(1L, "TestName", "TestValue");
		this.service.saveAndDelete(testItem);
		assertFalse(this.repository.existsById(testItem.getId()));
	}

	@Test
	public void findAllByLimit() {
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
	public void findFirstByOrderByIdAsc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findFirstByOrderByIdAsc().orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItems.get(0), foundItem);
	}

	@Test
	public void findFirstByOrderByIdDesc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findFirstByOrderByIdDesc().orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItems.get(expectedItems.size() - 1), foundItem);
	}

	@Test
	public void findTopByOrderByIdAsc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findTopByOrderByIdAsc().orElse(null);
		assertEquals(expectedItems.get(0), foundItem);
	}

	@Test
	public void findTopByOrderByIdDesc() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		TestItem foundItem = this.repository.findTopByOrderByIdDesc().orElse(null);
		assertEquals(expectedItems.get(expectedItems.size() - 1), foundItem);
	}

	@Test
	public void findTop10ByOrderByIdAsc() {
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
	public void findTop10ByOrderByIdDesc() {
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
	public void findDistinctNameRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		this.repository.save(new TestItem(2L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName2", "TestValue3"));
		List<TestItemNameRecord> foundItems = this.repository.findDistinctNameRecordByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameRecord::name).toList()).containsOnly("TestName1", "TestName2");
	}

	@Test
	public void findDistinctNameValueRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueRecord> foundItems = this.repository.findDistinctNameValueRecordByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueRecord::name).toList()).containsOnly("TestName1", "TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueRecord::value).toList()).containsOnly("TestValue2", "TestValue3");
	}

	@Test
	public void findDistinctNameValueProjectionByIdIn() {
		TestJoinedItem joinedItem1 = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName1"));
		TestJoinedItem joinedItem2 = this.joinedItemRepository.save(new TestJoinedItem(2L, "TestName2"));
		this.repository.save(new TestItem(1L, joinedItem1.getId(), Collections.emptyList(), "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, joinedItem2.getId(), Collections.emptyList(), "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, joinedItem2.getId(), Collections.emptyList(), "TestName3", "TestValue3"));
		List<TestItemNameValueJoinedItemProjection> foundItems = this.repository.findDistinctNameValueJoinedItemProjectionByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueJoinedItemProjection::getName).toList()).containsOnly("TestName1", "TestName3");
		assertThat(foundItems.stream().map(TestItemNameValueJoinedItemProjection::getValue).toList()).containsOnly("TestValue2", "TestValue3");
		assertThat(foundItems.stream().map(TestItemNameValueJoinedItemProjection::getJoinedItem).map(TestJoinedItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	public void findDistinctNameValueDtoByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueDto> foundItems = this.repository.findDistinctNameValueDtoByIdIn(List.of(1L, 2L, 3L));
		assertThat(foundItems.stream().map(TestItemNameValueDto::getName).toList()).containsOnly("TestName1", "TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueDto::getValue).toList()).containsOnly("TestValue2", "TestValue3");
	}

	@Test
	public void findDistinctDynamicProjectionRecordByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueRecord> foundItems = this.repository.findDistinctByIdIn(List.of(1L, 2L, 3L), TestItemNameValueRecord.class);
		assertThat(foundItems.stream().map(TestItemNameValueRecord::name).toList()).containsOnly("TestName1", "TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueRecord::value).toList()).containsOnly("TestValue2", "TestValue3");
	}

	@Test
	public void findDistinctDynamicProjectionInterfaceByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueProjection> foundItems = this.repository.findDistinctByIdIn(List.of(1L, 2L, 3L), TestItemNameValueProjection.class);
		assertThat(foundItems.stream().map(TestItemNameValueProjection::getName).toList()).containsOnly("TestName1", "TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueProjection::getValue).toList()).containsOnly("TestValue2", "TestValue3");
	}

	@Test
	public void findDistinctDynamicProjectionClassByIdIn() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue2"));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue3"));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		List<TestItemNameValueDto> foundItems = this.repository.findDistinctByIdIn(List.of(1L, 2L, 3L), TestItemNameValueDto.class);
		assertThat(foundItems.stream().map(TestItemNameValueDto::getName).toList()).containsOnly("TestName1", "TestName2");
		assertThat(foundItems.stream().map(TestItemNameValueDto::getValue).toList()).containsOnly("TestValue2", "TestValue3");
	}

	@Test
	public void findAllByIdBetween() {
		for (long i = 0; i < 100; i++) {
			this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i));
		}
		List<TestItem> foundItems = this.repository.findAllByIdBetween(80L, 90L);
		assertThat(foundItems.stream().map(TestItem::getId).toList())
				.containsExactly(80L, 81L, 82L, 83L, 84L, 85L, 86L, 87L, 88L, 89L, 90L);
	}

	@Test
	public void findByActiveIsTrue() {
		this.repository.save(new TestItem(1L, true));
		this.repository.save(new TestItem(2L, true));
		this.repository.save(new TestItem(3L, false));
		this.repository.save(new TestItem(4L, false));
		List<TestItem> foundItems = this.repository.findByActiveIsTrue();
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	public void findByActiveIsFalse() {
		this.repository.save(new TestItem(1L, true));
		this.repository.save(new TestItem(2L, true));
		this.repository.save(new TestItem(3L, false));
		this.repository.save(new TestItem(4L, false));
		List<TestItem> foundItems = this.repository.findByActiveIsFalse();
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L, 4L);
	}

	@Test
	public void findAllItemProjectionByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemProjection> foundItems = this.repository.findAllItemProjectionByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.toList(), Sort.by(Direction.DESC, "id"));
		assertThat(foundItems).hasSameSizeAs(expectedItems);
		for (int i = 0; i < foundItems.size(); i++) {
			TestItemProjection foundItem = foundItems.get(i);
			TestItem expectedItem = expectedItems.get(expectedItems.size() - 1 - i);
			assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
			assertThat(foundItem.getName()).isEqualTo(expectedItem.getName());
		}
	}

	@Test
	public void findAllItemDtoByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemDto> foundItems = this.repository.findAllItemDtoByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.toList(), Sort.by(Direction.ASC, "id"));
		assertThat(foundItems).hasSameSizeAs(expectedItems);
		for (int i = 0; i < foundItems.size(); i++) {
			TestItemDto foundItem = foundItems.get(i);
			TestItem expectedItem = expectedItems.get(expectedItems.size() - 1 - i);
			assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
			assertThat(foundItem.getName()).isEqualTo(expectedItem.getName());
		}
	}

	@Test
	public void findAllItemRecordByIdIn() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItemRecord> foundItems = this.repository.findAllItemRecordByIdIn(expectedItems.stream()
				.map(TestItem::getId)
				.toList());
		assertThat(foundItems).hasSameSizeAs(expectedItems);
		for (int i = 0; i < foundItems.size(); i++) {
			TestItemRecord foundItem = foundItems.get(i);
			TestItem expectedItem = expectedItems.get(i);
			assertThat(foundItem.id()).isEqualTo(expectedItem.getId());
			assertThat(foundItem.name()).isEqualTo(expectedItem.getName());
		}
	}

	@Test
	public void findAllCountByIdInPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = PageRequest.of(0, 5, Sort.by(Order.desc("id"), Order.asc("name")));
		List<Long> expectedIds = expectedItems.stream()
				.map(TestItem::getId)
				.toList();
		do {
			Page<TestItem> foundItems = this.repository.findAllCountByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		} while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findAllCountCachedByIdInPageable() {
		Set<TestItem> expectedItems = new HashSet<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		Pageable pageable = PageRequest.of(0, 5, Sort.by(Order.desc("id"), Order.asc("name")));
		List<Long> expectedIds = expectedItems.stream()
				.map(TestItem::getId)
				.toList();
		do {
			Page<TestItem> foundItems = this.repository.findAllCountCachedByIdIn(expectedIds, pageable);
			for (TestItem item : foundItems) {
				assertTrue(expectedItems.remove(item));
			}
			pageable = foundItems.nextPageable();
		} while (pageable.isPaged());
		assertEquals(0, expectedItems.size());
	}

	@Test
	public void findFirst2Sql() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst2Sql(PageRequest.of(0, 3, Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst2Sql(PageRequest.of(1, 3, Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	public void findFirst3Sql() {
		TestItem item1 = this.repository.save(new TestItem(1L, "TestName1", "TestValue1"));
		TestItem item2 = this.repository.save(new TestItem(2L, "TestName2", "TestValue2"));
		TestItem item3 = this.repository.save(new TestItem(3L, "TestName3", "TestValue3"));
		Page<TestItem> firstPage = this.repository.findFirst3Sql(PageRequest.of(0, 2, Direction.ASC, "id"));
		assertThat(firstPage.getContent()).contains(item1, item2);
		Page<TestItem> secondPage = this.repository.findFirst3Sql(PageRequest.of(1, 2, Direction.ASC, "id"));
		assertThat(secondPage).contains(item3);
	}

	@Test
	public void findAllSqlLimit() {
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
	public void findAllByNameLike() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameLike("%LIMITED");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	public void findAllByNameNotLike() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameNotLike("%LIMITED");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	public void findAllByCitiesContaining() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1", List.of("City1", "City2")));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue2", List.of("City1", "City3")));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3", List.of("City2", "City3")));
		List<TestItem> foundItems = this.repository.findAllByCitiesContaining("City1");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	public void findAllByCitiesNotContaining() {
		this.repository.save(new TestItem(1L, "TestName1", "TestValue1", List.of("City1", "City2")));
		this.repository.save(new TestItem(2L, "TestName2", "TestValue2", List.of("City1", "City3")));
		this.repository.save(new TestItem(3L, "TestName3", "TestValue3", List.of("City2", "City3")));
		List<TestItem> foundItems = this.repository.findAllByCitiesNotContaining("City1");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	public void findAllByNameContaining() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameContaining("LIMIT");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	public void findAllByNameNotContaining() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameNotContaining("LIMIT");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	public void findAllByNameStartingWith() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameStartingWith("Test");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(3L);
	}

	@Test
	public void findAllByNameEndingWith() {
		this.repository.save(new TestItem(1L, "LIMITED", "TestValue1"));
		this.repository.save(new TestItem(2L, "UNLIMITED", "TestValue2"));
		this.repository.save(new TestItem(3L, "TestName", "TestValue3"));
		List<TestItem> foundItems = this.repository.findAllByNameEndingWith("ED");
		assertThat(foundItems.stream().map(TestItem::getId).toList()).containsOnly(1L, 2L);
	}

	@Test
	public void findByNameWithJoinedItems() {
		TestJoinedItem nestedJoinedItem = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName1"));
		TestJoinedItem joinedItem = this.joinedItemRepository.save(new TestJoinedItem(2L, nestedJoinedItem.getId(), "TestName2"));
		Map<Long, TestJoinedItem> expectedJoinedItems = new HashMap<>();
		expectedJoinedItems.put(3L, this.joinedItemRepository.save(new TestJoinedItem(3L, nestedJoinedItem.getId(), "TestName3")));
		expectedJoinedItems.put(4L, this.joinedItemRepository.save(new TestJoinedItem(4L, nestedJoinedItem.getId(), "TestName4")));
		expectedJoinedItems.put(5L, this.joinedItemRepository.save(new TestJoinedItem(5L, nestedJoinedItem.getId(), "TestName5")));
		List<Long> joinedItemIds = new ArrayList<>(expectedJoinedItems.keySet());
		TestItem expectedItem = this.repository.save(new TestItem(1L, joinedItem.getId(), joinedItemIds, "TestName", "TestValue"));
		TestItem foundItem = this.repository.findByName("TestName").orElse(null);
		assertThat(foundItem).isNotNull();
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
	public void findProjectionByNameWithJoinedItems() {
		TestJoinedItem nestedJoinedItem = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName1"));
		TestJoinedItem joinedItem = this.joinedItemRepository.save(new TestJoinedItem(2L, nestedJoinedItem.getId(), "TestName2"));
		Map<Long, TestJoinedItem> expectedJoinedItems = new HashMap<>();
		expectedJoinedItems.put(3L, this.joinedItemRepository.save(new TestJoinedItem(3L, nestedJoinedItem.getId(), "TestName3")));
		expectedJoinedItems.put(4L, this.joinedItemRepository.save(new TestJoinedItem(4L, nestedJoinedItem.getId(), "TestName4")));
		expectedJoinedItems.put(5L, this.joinedItemRepository.save(new TestJoinedItem(5L, nestedJoinedItem.getId(), "TestName5")));
		List<Long> joinedItemIds = new ArrayList<>(expectedJoinedItems.keySet());
		TestItem expectedItem = this.repository.save(new TestItem(1L, joinedItem.getId(), joinedItemIds, "TestName", "TestValue"));
		TestItemProjectionWithJoinedItems foundItem = this.repository.findProjectionByName("TestName");
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getId()).isEqualTo(expectedItem.getId());
		assertThat(foundItem.getJoinedItem().getId()).isEqualTo(joinedItem.getId());
		assertThat(foundItem.getJoinedItem().getName()).isEqualTo(joinedItem.getName());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem()).isNotNull();
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem().getId()).isEqualTo(nestedJoinedItem.getId());
		assertThat(foundItem.getJoinedItem().getNestedJoinedItem().getName()).isEqualTo(nestedJoinedItem.getName());
		assertThat(foundItem.getJoinedItems()).hasSize(expectedJoinedItems.size());
		for (TestJoinedItemProjection foundJoinedItem : foundItem.getJoinedItems()) {
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
	public void findOneByExample() {
		TestJoinedItem nestedItem = this.joinedItemRepository.save(new TestJoinedItem(1L, "TestName"));
		TestItem expectedItem = this.repository.save(new TestItem(1L, nestedItem, "TestName", "TestValue"));
		TestItem foundItem = this.repository.findOne(Example.of(expectedItem)).orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.getId());
		assertEquals(expectedItem.getName(), foundItem.getName());
		assertEquals(expectedItem.getValue(), foundItem.getValue());
		assertNotNull(foundItem.getNestedItem());
		assertEquals(nestedItem.getId(), foundItem.getNestedItem().getId());
		assertEquals(nestedItem.getName(), foundItem.getNestedItem().getName());
	}

	@Test
	public void findByFluentQueryExampleRecordProjection() {
		TestItem expectedItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItemRecord foundItem = this.repository.findBy(Example.of(expectedItem),
						query -> query.project(List.of("id", "name")).as(TestItemRecord.class).one())
				.orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.id());
		assertEquals(expectedItem.getName(), foundItem.name());
	}

	@Test
	public void findByFluentQueryExampleInterfaceProjection() {
		TestItem expectedItem = this.repository.save(new TestItem(1L, "TestName", "TestValue"));
		TestItemProjection foundItem = this.repository.findBy(Example.of(expectedItem),
						query -> query.project(List.of("id", "name")).as(TestItemProjection.class).one())
				.orElse(null);
		assertNotNull(foundItem);
		assertEquals(expectedItem.getId(), foundItem.getId());
		assertEquals(expectedItem.getName(), foundItem.getName());
	}

	@Configuration
	@EnableReindexerRepositories(basePackageClasses = TestItemReindexerRepository.class, considerNestedRepositories = true)
	@EnableTransactionManagement
	@ComponentScan(basePackageClasses = TestItemTransactionalService.class)
	static class TestConfig extends ReindexerConfigurationSupport {

		@Bean
		Reindexer reindexer() {
			return ReindexerConfiguration.builder()
					.url("cproto://localhost:" + reindexer.getMappedPort(RPC_PORT) + "/" + DATABASE_NAME)
					.getReindexer();
		}

		@Bean
		ReindexerTransactionManager<TestItem> txManager(Reindexer reindexer) throws ClassNotFoundException {
			return new ReindexerTransactionManager<>(reindexer, reindexerMappingContext(), TestItem.class);
		}

	}

	@Transactional(transactionManager = "txManager")
	@Service
	public static class TestItemTransactionalService {

		private final TestItemReindexerRepository repository;

		public TestItemTransactionalService(TestItemReindexerRepository repository) {
			this.repository = repository;
		}

		public TestItem save(TestItem item) {
			return this.repository.save(item);
		}

		public void saveAndDelete(TestItem testItem) {
			this.repository.save(testItem);
			this.repository.delete(testItem);
		}

		public void saveExceptionally(TestItem item) {
			this.repository.save(item);
			throw new IllegalStateException();
		}

	}

	@Repository
	interface TestItemReindexerRepository extends ReindexerRepository<TestItem, Long> {

		Optional<TestItem> findByName(String name);

		TestItemProjectionWithJoinedItems findProjectionByName(String name);

		Optional<TestItem> findByNameAndValue(String name, String value);

		Optional<TestItem> findByNameOrValue(String name, String value);

		Optional<TestItem> findByTestEnumString(TestEnum testEnum);

		Optional<TestItem> findByTestEnumOrdinal(TestEnum testEnum);

		ResultIterator<TestItem> findIteratorByName(String name);

		@Query("SELECT * FROM items WHERE name = ?1")
		ResultIterator<TestItem> findIteratorSqlByName(String name);

		@Query("SELECT * FROM items WHERE name = :name")
		ResultIterator<TestItem> findIteratorSqlByNameParam(@Param("name") String name);

		@Query(value = "UPDATE items SET name = ?1 WHERE id = ?2", update = true)
		void updateNameSql(String name, Long id);

		@Query(value = "UPDATE items SET name = :name WHERE id = :id", update = true)
		void updateNameSqlParam(@Param("name") String name, @Param("id") Long id);

		TestItem getByName(String name);

		@Query("SELECT * FROM items WHERE name = ?1")
		Optional<TestItem> findOneSqlByName(String name);

		@Query("SELECT * FROM items WHERE name = :name")
		Optional<TestItem> findOneSqlByNameParam(@Param("name") String name);

		@Query("SELECT * FROM items WHERE name = ?11 AND value = ?12")
		Optional<TestItem> findOneSqlByNameAndValueManyParams(String name1, String name2, String name3, String name4, String name5,
				String name6, String name7, String name8, String name9, String name10, String name11, String value);

		@Query("SELECT * FROM items WHERE id = ?1 AND name = ?2 AND value = ?3")
		Optional<TestItem> findOneSqlByIdAndNameAndValue(Long id, String name, String value);

		@Query("SELECT * FROM items WHERE id = :id AND name = :name AND value = :value")
		Optional<TestItem> findOneSqlByIdAndNameAndValueParam(@Param("id") Long id, @Param("name") String name, @Param("value") String value);

		@Query("SELECT * FROM items WHERE id = ?#{[0]} AND name = ?#{[1]} AND value = ?#{[2]}")
		Optional<TestItem> findOneSqlSpelByIdAndNameAndValueParam(Long id, String name, String value);

		@Query("SELECT * FROM items WHERE id = :#{#item.id} AND name = :#{#item.name} AND value = :#{#item.value}")
		Optional<TestItem> findOneSqlSpelByItemIdAndNameAndValueParam(TestItem item);

		@Query("SELECT * FROM items WHERE id = ?2 AND name = ?3 AND value = ?1")
		Optional<TestItem> findOneSqlByIdAndNameAndValue(String value, Long id, String name);

		@Query("SELECT * FROM items WHERE id = :id AND name = :name AND value = :value")
		Optional<TestItem> findOneSqlByIdAndNameAndValueParam(@Param("value") String value, @Param("id") Long id, @Param("name") String name);

		@Query("SELECT * FROM items WHERE name = ?1")
		TestItem getOneSqlByName(String name);

		@Query("SELECT * FROM items WHERE name = :name")
		TestItem getOneSqlByNameParam(@Param("name") String name);

		@Query("SELECT * FROM items")
		List<TestItem> findAllListSql();

		@Query("SELECT * FROM items")
		Set<TestItem> findAllSetSql();

		@Query("SELECT * FROM items")
		Stream<TestItem> findAllStreamSql();

		List<TestItem> findByIdIn(List<Long> ids);

		List<TestItem> findByIdIn(long... ids);

		List<TestItem> findByIdNotIn(List<Long> ids);

		List<TestItem> findByTestEnumStringIn(List<TestEnum> values);

		List<TestItem> findByTestEnumStringIn(TestEnum... values);

		List<TestItem> findByTestEnumOrdinalIn(List<TestEnum> values);

		List<TestItem> findByTestEnumOrdinalIn(TestEnum... values);

		boolean existsByName(String name);

		int countByValue(String value);

		int countByIdIn(List<Long> ids);

		void deleteByName(String name);

		List<TestItem> findAllByIdIn(List<Long> ids, Sort sort);

		List<TestItem> findByIdIn(List<Long> ids, Pageable pageable);

		Page<TestItem> findPageByIdIn(List<Long> ids, Pageable pageable);

		Page<TestItem> findFirst2By(Pageable pageable);

		Page<TestItem> findFirst3By(Pageable pageable);

		List<TestItemProjection> findItemProjectionByIdIn(List<Long> ids);

		List<TestItemDto> findItemDtoByIdIn(List<Long> ids);

		List<TestItemPreferredConstructorDto> findItemPreferredConstructorDtoByIdIn(List<Long> ids);

		List<TestItemRecord> findItemRecordByIdIn(List<Long> ids);

		List<TestItemPreferredConstructorRecord> findItemPreferredConstructorRecordByIdIn(List<Long> ids);

		<T> List<T> findByIdIn(List<Long> ids, Class<T> type);

		List<TestItem> findAllBy(Limit limit);

		Optional<TestItem> findFirstByOrderByIdAsc();

		Optional<TestItem> findFirstByOrderByIdDesc();

		Optional<TestItem> findTopByOrderByIdAsc();

		Optional<TestItem> findTopByOrderByIdDesc();

		List<TestItem> findTop10ByOrderByIdAsc();

		List<TestItem> findTop10ByOrderByIdDesc();

		List<TestItemNameRecord> findDistinctNameRecordByIdIn(List<Long> ids);

		List<TestItemNameValueRecord> findDistinctNameValueRecordByIdIn(List<Long> ids);

		List<TestItemNameValueProjection> findDistinctNameValueProjectionByIdIn(List<Long> ids);

		List<TestItemNameValueJoinedItemProjection> findDistinctNameValueJoinedItemProjectionByIdIn(List<Long> ids);

		List<TestItemNameValueDto> findDistinctNameValueDtoByIdIn(List<Long> ids);

		<T> List<T> findDistinctByIdIn(List<Long> ids, Class<T> type);

		List<TestItem> findAllByIdBetween(Long start, Long end);

		List<TestItem> findByActiveIsTrue();

		List<TestItem> findByActiveIsFalse();

		@Query("SELECT id, name FROM items WHERE id IN :ids")
		List<TestItemProjection> findAllItemProjectionByIdIn(List<Long> ids, Sort sort);

		@Query("SELECT id, name FROM items WHERE id IN :ids ORDER BY id DESC")
		List<TestItemDto> findAllItemDtoByIdIn(List<Long> ids, Sort sort);

		@Query("SELECT id, name FROM items WHERE id IN :ids")
		List<TestItemRecord> findAllItemRecordByIdIn(List<Long> ids);

		@Query("SELECT *, COUNT(*) FROM items WHERE id IN :ids")
		Page<TestItem> findAllCountByIdIn(List<Long> ids, Pageable pageable);

		@Query("SELECT *, COUNT_CACHED(*) FROM items WHERE id IN :ids")
		Page<TestItem> findAllCountCachedByIdIn(List<Long> ids, Pageable pageable);

		@Query("SELECT *, COUNT_CACHED(*) FROM items LIMIT 2")
		Page<TestItem> findFirst2Sql(Pageable pageable);

		@Query("SELECT *, COUNT_CACHED(*) FROM items LIMIT 3")
		Page<TestItem> findFirst3Sql(Pageable pageable);

		@Query("SELECT * FROM items")
		List<TestItem> findAllSqlLimit(Limit limit);

		List<TestItem> findAllByNameLike(String pattern);

		List<TestItem> findAllByNameNotLike(String pattern);

		List<TestItem> findAllByCitiesContaining(String city);

		List<TestItem> findAllByCitiesNotContaining(String city);

		List<TestItem> findAllByNameContaining(String text);

		List<TestItem> findAllByNameNotContaining(String text);

		List<TestItem> findAllByNameStartingWith(String text);

		List<TestItem> findAllByNameEndingWith(String text);
	}

	@Repository
	interface TestJoinedItemRepository extends ReindexerRepository<TestJoinedItem, Long> {

	}

	@Namespace(name = NAMESPACE_NAME)
	public static class TestItem {

		@Reindex(name = "id", isPrimaryKey = true)
		private Long id;

		@Reindex(name = "name")
		private String name;

		@Reindex(name = "value")
		private String value;

		@Enumerated(EnumType.STRING)
		@Reindex(name = "testEnumString")
		private TestEnum testEnumString;

		@Enumerated(EnumType.ORDINAL)
		@Reindex(name = "testEnumOrdinal")
		private TestEnum testEnumOrdinal;

		@Reindex(name = "cities")
		private List<String> cities = new ArrayList<>();

		@Reindex(name = "active")
		private boolean active;

		private Long joinedItemId;

		private List<Long> joinedItemIds = new ArrayList<>();

		@Reindex(name = "nested")
		private TestJoinedItem nestedItem;

		@Transient
		@NamespaceReference(indexName = "joinedItemId", joinType = JoinType.LEFT, lazy = true)
		private TestJoinedItem joinedItem;

		@Transient
		@NamespaceReference(indexName = "joinedItemIds", joinType = JoinType.LEFT)
		private List<TestJoinedItem> joinedItems = new ArrayList<>();

		public TestItem() {
		}

		public TestItem(Long id, String name, String value) {
			this.id = id;
			this.name = name;
			this.value = value;
		}

		public TestItem(Long id, boolean active) {
			this.id = id;
			this.active = active;
		}

		public TestItem(Long id, String name, String value, List<String> cities) {
			this.id = id;
			this.name = name;
			this.value = value;
			this.cities = cities;
		}

		public TestItem(Long id, String name, String value, TestEnum testEnumString, TestEnum testEnumOrdinal) {
			this.id = id;
			this.name = name;
			this.value = value;
			this.testEnumString = testEnumString;
			this.testEnumOrdinal = testEnumOrdinal;
		}

		public TestItem(Long id, Long joinedItemId, List<Long> joinedItemIds, String name, String value) {
			this.id = id;
			this.joinedItemId = joinedItemId;
			this.joinedItemIds = joinedItemIds;
			this.name = name;
			this.value = value;
		}

		public TestItem(Long id, TestJoinedItem nestedItem, String name, String value) {
			this.id = id;
			this.nestedItem = nestedItem;
			this.name = name;
			this.value = value;
		}

		public Long getId() {
			return this.id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public TestEnum getTestEnumString() {
			return testEnumString;
		}

		public void setTestEnumString(TestEnum testEnumString) {
			this.testEnumString = testEnumString;
		}

		public TestEnum getTestEnumOrdinal() {
			return testEnumOrdinal;
		}

		public void setTestEnumOrdinal(TestEnum testEnumOrdinal) {
			this.testEnumOrdinal = testEnumOrdinal;
		}

		public List<String> getCities() {
			return this.cities;
		}

		public void setCities(List<String> cities) {
			this.cities = cities;
		}

		public boolean isActive() {
			return this.active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		public Long getJoinedItemId() {
			return this.joinedItemId;
		}

		public void setJoinedItemId(Long joinedItemId) {
			this.joinedItemId = joinedItemId;
		}

		public List<Long> getJoinedItemIds() {
			return this.joinedItemIds;
		}

		public void setJoinedItemIds(List<Long> joinedItemIds) {
			this.joinedItemIds = joinedItemIds;
		}

		public TestJoinedItem getNestedItem() {
			return this.nestedItem;
		}

		public void setNestedItem(TestJoinedItem nestedItem) {
			this.nestedItem = nestedItem;
		}

		public TestJoinedItem getJoinedItem() {
			return this.joinedItem;
		}

		public void setJoinedItem(TestJoinedItem joinedItem) {
			this.joinedItem = joinedItem;
		}

		public List<TestJoinedItem> getJoinedItems() {
			return this.joinedItems;
		}

		public void setJoinedItems(List<TestJoinedItem> joinedItems) {
			this.joinedItems = joinedItems;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			TestItem item = (TestItem) o;
			return active == item.active && Objects.equals(id, item.id) && Objects.equals(name, item.name)
					&& Objects.equals(value, item.value) && testEnumString == item.testEnumString
					&& testEnumOrdinal == item.testEnumOrdinal && Objects.equals(cities, item.cities)
					&& Objects.equals(nestedItem, item.nestedItem)
					&& Objects.equals(joinedItemId, item.joinedItemId) && Objects.equals(joinedItemIds, item.joinedItemIds);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, name, value, testEnumString, testEnumOrdinal, cities,
					active, nestedItem, joinedItemId, joinedItemIds);
		}

		@Override
		public String toString() {
			return "TestItem{" +
					"id=" + this.id +
					", name='" + this.name + '\'' +
					", value='" + this.value + '\'' +
					", testEnumString=" + this.testEnumString +
					", testEnumOrdinal=" + this.testEnumOrdinal +
					", cities=" + this.cities +
					", active=" + this.active +
					'}';
		}

	}

	@Namespace(name = "test_joined_items")
	public static class TestJoinedItem {

		@Reindex(name = "id", isPrimaryKey = true)
		private Long id;

		@Reindex(name = "name")
		private String name;

		private Long nestedJoinedItemId;

		@Transient
		@NamespaceReference(indexName = "nestedJoinedItemId", joinType = JoinType.LEFT, fetch = true)
		private TestJoinedItem nestedJoinedItem;

		public TestJoinedItem() {
		}

		public TestJoinedItem(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		public TestJoinedItem(Long id, Long nestedJoinedItemId, String name) {
			this.id = id;
			this.nestedJoinedItemId = nestedJoinedItemId;
			this.name = name;
		}

		public Long getId() {
			return this.id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Long getNestedJoinedItemId() {
			return this.nestedJoinedItemId;
		}

		public void setNestedJoinedItemId(Long nestedJoinedItemId) {
			this.nestedJoinedItemId = nestedJoinedItemId;
		}

		public TestJoinedItem getNestedJoinedItem() {
			return this.nestedJoinedItem;
		}

		public void setNestedJoinedItem(TestJoinedItem nestedJoinedItem) {
			this.nestedJoinedItem = nestedJoinedItem;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			TestJoinedItem that = (TestJoinedItem) o;
			return Objects.equals(this.id, that.id) && Objects.equals(this.name, that.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.id, this.name);
		}

		@Override
		public String toString() {
			return "TestJoinedItem{" +
					"id=" + this.id +
					", name='" + this.name + '\'' +
					'}';
		}

	}

	interface TestItemProjection {

		Long getId();

		String getName();

	}

	interface TestItemNameValueProjection {

		String getName();

		String getValue();

	}

	interface TestItemNameValueJoinedItemProjection {

		String getName();

		String getValue();

		TestJoinedItem getJoinedItem();

	}

	public static class TestItemDto {

		private final Long id;

		private final String name;

		public TestItemDto(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		public Long getId() {
			return this.id;
		}

		public String getName() {
			return this.name;
		}

	}

	public static class TestItemNameValueDto {

		private final String name;

		private final String value;

		public TestItemNameValueDto(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return this.name;
		}

		public String getValue() {
			return this.value;
		}

	}

	public static class TestItemPreferredConstructorDto {

		private Long id;

		private String name;

		public TestItemPreferredConstructorDto(String name) {
			this.name = name;
		}

		@PersistenceCreator
		public TestItemPreferredConstructorDto(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		public Long getId() {
			return this.id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

	public record TestItemRecord(Long id, String name) {
	}

	public record TestItemNameRecord(String name) {
	}

	public record TestItemNameValueRecord(String name, String value) {
	}

	public record TestItemPreferredConstructorRecord(Long id, String name) {

		@PersistenceCreator
		TestItemPreferredConstructorRecord(String name) {
			this(null, name);
		}

	}

	public static class TestItemProjectionWithJoinedItems {

		private final Long id;

		private final TestJoinedItemProjection joinedItem;

		private final Collection<TestJoinedItemProjection> joinedItems;

		public TestItemProjectionWithJoinedItems(Long id, TestJoinedItemProjection joinedItem, Collection<TestJoinedItemProjection> joinedItems) {
			this.id = id;
			this.joinedItem = joinedItem;
			this.joinedItems = joinedItems;
		}

		public Long getId() {
			return this.id;
		}

		public TestJoinedItemProjection getJoinedItem() {
			return this.joinedItem;
		}

		public Collection<TestJoinedItemProjection> getJoinedItems() {
			return this.joinedItems;
		}

	}

	public static class TestJoinedItemProjection {

		private final Long id;

		private final String name;

		private final TestJoinedItemProjection nestedJoinedItem;

		public TestJoinedItemProjection(Long id, String name, TestJoinedItemProjection nestedJoinedItem) {
			this.id = id;
			this.name = name;
			this.nestedJoinedItem = nestedJoinedItem;
		}

		public Long getId() {
			return this.id;
		}

		public String getName() {
			return this.name;
		}

		public TestJoinedItemProjection getNestedJoinedItem() {
			return this.nestedJoinedItem;
		}

	}

	public enum TestEnum {
		TEST_CONSTANT_1,
		TEST_CONSTANT_2,
		TEST_CONSTANT_3,
	}

	public static class CreateDatabase {

		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
