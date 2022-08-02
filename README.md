Spring Data Reindexer
====================
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.evgeniycheban/spring-data-reindexer/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.evgeniycheban/spring-data-reindexer)

Provides Spring Data approach to work with Reindexer database.

## Maven

```xml

<dependency>
	<groupId>io.github.evgeniycheban</groupId>
	<artifactId>spring-data-reindexer</artifactId>
	<version>${spring-data-reindexer.version}</version>
</dependency>
```

## Usage

Here is an example of basic `spring-data-reindexer` usage:

### Configuration

```java
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;

@Configuration
@EnableReindexerRepositories
public class ReindexerConfig {

	@Bean
	public Reindexer reindexer() {
		return ReindexerConfiguration.builder()
				.url("cproto://localhost:6534/items")
				.getReindexer();
	}

}
```

### Entity

```java
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.reindexer.core.mapping.Namespace;

@Namespace(name = "items")
public class Item {

	@Reindex(name = "id", isPrimaryKey = true)
	private Long id;

	@Reindex(name = "name")
	private String name;

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
```

### Repository

```java
import java.util.Optional;

import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends ReindexerRepository<Item, Long> {

	Optional<Item> findByName(String name);

}
```

The `ItemRepository` can be injected into spring-managed beans using `@Autowired` or
another Spring Framework approach.

```java
@Autowired
private ItemRepository repository;
```

More examples can be found
[here](https://github.com/evgeniycheban/spring-data-reindexer/blob/main/src/test/java/org/springframework/data/reindexer/repository/ReindexerRepositoryTests.java).

## Transactions

Transactions are implemented using `@Transactional` annotation approach, to enable
transaction management support `@EnableTransactionManagement` annotation should be placed
on the `@Configuration` class and `ReindexerTransactionManager` bean should be defined in
the context.

Note that `ReindexerTransactionManager` manages transactions for a single namespace,
therefore it should be defined with a domain class that is mapped to a Reindexer
namespace.

Here is an example of basic transaction management usage:

### Configuration

```java
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.reindexer.ReindexerTransactionManager;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableReindexerRepositories
@EnableTransactionManagement
public class TransactionalReindexerConfig {

	@Bean
	public Reindexer reindexer() {
		return ReindexerConfiguration.builder()
				.url("cproto://localhost:6534/items")
				.getReindexer();
	}

	@Bean
	public ReindexerTransactionManager<Item> itemTxManager(Reindexer reindexer) {
		return new ReindexerTransactionManager<>(reindexer, Item.class);
	}

}
```

### Transactional Service

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemTransactionalService {

	private final ItemRepository repository;

	public ItemTransactionalService(ItemRepository repository) {
		this.repository = repository;
	}

	@Transactional(transactionManager = "itemTxManager")
	public Item save(Item item) {
		return this.repository.save(item);
	}

}
```

## Work in progress

- Binding parameters for a native query using `@Param` annotation.
- Support more return types for `ReindexerRepository`.
