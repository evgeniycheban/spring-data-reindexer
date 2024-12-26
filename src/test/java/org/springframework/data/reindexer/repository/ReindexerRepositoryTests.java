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
import java.util.HashMap;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.reindexer.ReindexerTransactionManager;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
	public void getByNameWhenNotExistsThenException() {
		assertThrows(IllegalStateException.class, () -> this.repository.getByName("notExists"),
				"Exactly one item expected, but there is zero");
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
	public void findByIdContaining() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findByIdContaining(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(expectedItems.size(), foundItems.size());
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
	public void findByIdNotContaining() {
		List<TestItem> expectedItems = new ArrayList<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.add(this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		List<TestItem> foundItems = this.repository.findByIdNotContaining(expectedItems.stream()
				.map(TestItem::getId)
				.collect(Collectors.toList()));
		assertEquals(0, foundItems.size());
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

	@Configuration
	@EnableReindexerRepositories(basePackageClasses = TestItemReindexerRepository.class, considerNestedRepositories = true)
	@EnableTransactionManagement
	@ComponentScan(basePackageClasses = TestItemTransactionalService.class)
	static class TestConfig {

		@Bean
		Reindexer reindexer() {
			return ReindexerConfiguration.builder()
					.url("cproto://localhost:" + reindexer.getMappedPort(RPC_PORT) + "/" + DATABASE_NAME)
					.getReindexer();
		}

		@Bean
		ReindexerTransactionManager<TestItem> txManager(Reindexer reindexer) {
			return new ReindexerTransactionManager<>(reindexer, TestItem.class);
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

		public void saveExceptionally(TestItem item) {
			this.repository.save(item);
			throw new IllegalStateException();
		}

	}

	@Repository
	interface TestItemReindexerRepository extends ReindexerRepository<TestItem, Long> {

		Optional<TestItem> findByName(String name);

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

		List<TestItem> findByIdContaining(List<Long> ids);

		List<TestItem> findByIdNotIn(List<Long> ids);

		List<TestItem> findByIdNotContaining(List<Long> ids);

		List<TestItem> findByTestEnumStringIn(List<TestEnum> values);

		List<TestItem> findByTestEnumStringIn(TestEnum... values);

		List<TestItem> findByTestEnumOrdinalIn(List<TestEnum> values);

		List<TestItem> findByTestEnumOrdinalIn(TestEnum... values);
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

		public TestItem() {
		}

		public TestItem(Long id, String name, String value) {
			this.id = id;
			this.name = name;
			this.value = value;
		}

		public TestItem(Long id, String name, String value, TestEnum testEnumString, TestEnum testEnumOrdinal) {
			this.id = id;
			this.name = name;
			this.value = value;
			this.testEnumString = testEnumString;
			this.testEnumOrdinal = testEnumOrdinal;
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

		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			TestItem testItem = (TestItem) o;
			return Objects.equals(id, testItem.id) && Objects.equals(name, testItem.name) && Objects.equals(value, testItem.value)
					&& testEnumString == testItem.testEnumString && testEnumOrdinal == testItem.testEnumOrdinal;
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, name, value, testEnumString, testEnumOrdinal);
		}

		@Override
		public String toString() {
			return "TestItem{" +
					"id=" + this.id +
					", name='" + this.name + '\'' +
					", value='" + this.value + '\'' +
					", testEnumString=" + this.testEnumString +
					", testEnumOrdinal=" + this.testEnumOrdinal +
					'}';
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
