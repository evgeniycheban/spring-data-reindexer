package org.springframework.data.reindexer.repository;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
