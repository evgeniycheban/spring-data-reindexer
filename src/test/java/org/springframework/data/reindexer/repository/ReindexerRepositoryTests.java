package org.springframework.data.reindexer.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.rt.restream.reindexer.CloseableIterator;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReindexerRepository}.
 *
 * @author Evgeniy Cheban
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
class ReindexerRepositoryTests {

	@Autowired
	TestItemReindexerRepository repository;

	@AfterEach
	void tearDown() {
		this.repository.deleteAll();
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
	public void findIteratorByName() {
		TestItem testItem = this.repository.save(new TestItem(1L, "TestValue", null));
		try (CloseableIterator<TestItem> it = this.repository.findIteratorByName("TestValue")) {
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
		try (CloseableIterator<TestItem> it = this.repository.findIteratorSqlByName("TestValue")) {
			assertTrue(it.hasNext());
			TestItem item = it.next();
			assertEquals(testItem.getId(), item.getId());
			assertEquals(testItem.getName(), item.getName());
			assertEquals(testItem.getValue(), item.getValue());
			assertFalse(it.hasNext());
		}
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
	public void iterator() {
		Map<Long, TestItem> expectedItems = new HashMap<>();
		for (long i = 0; i < 100; i++) {
			expectedItems.put(i, this.repository.save(new TestItem(i, "TestName" + i, "TestValue" + i)));
		}
		try (CloseableIterator<TestItem> it = this.repository.iterator()) {
			while (it.hasNext()) {
				TestItem actual = it.next();
				TestItem expected = expectedItems.remove(actual.getId());
				assertNotNull(expected);
				assertEquals(expected.getId(), actual.getId());
				assertEquals(expected.getName(), actual.getName());
				assertEquals(expected.getValue(), actual.getValue());
			}
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

	@Configuration
	@EnableReindexerRepositories(basePackageClasses = TestItemReindexerRepository.class, considerNestedRepositories = true)
	static class TestConfig {

		@Bean
		Reindexer server() {
			return ReindexerConfiguration.builder()
					.url("builtinserver://items")
					.getReindexer();
		}

		@Bean
		Reindexer reindexer() {
			return ReindexerConfiguration.builder()
					.url("cproto://localhost:6534/items")
					.getReindexer();
		}

	}

	@Repository
	interface TestItemReindexerRepository extends ReindexerRepository<TestItem, Long> {

		Optional<TestItem> findByName(String name);

		Optional<TestItem> findByNameAndValue(String name, String value);

		Optional<TestItem> findByNameOrValue(String name, String value);

		CloseableIterator<TestItem> findIteratorByName(String name);

		@Query("SELECT * FROM items WHERE name = '%s'")
		CloseableIterator<TestItem> findIteratorSqlByName(String name);

		@Query(value = "UPDATE items SET name = '%s' WHERE id = %d", update = true)
		void updateNameSql(String name, Long id);

	}

	@Namespace(name = "items")
	public static class TestItem {

		@Reindex(name = "id", isPrimaryKey = true)
		private Long id;

		@Reindex(name = "name")
		private String name;

		@Reindex(name = "value")
		private String value;

		public TestItem() {
		}

		public TestItem(Long id, String name, String value) {
			this.id = id;
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

		@Override
		public String toString() {
			return "TestItem{" +
					"id=" + this.id +
					", name='" + this.name + '\'' +
					", value='" + this.value + '\'' +
					'}';
		}

	}

}
