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

Configuration:

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
	Reindexer reindexer() {
		return ReindexerConfiguration.builder()
				.url("cproto://localhost:6534/items")
				.getReindexer();
	}

}
```

Entity:

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

Repository:

```java
import java.util.Optional;

import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends ReindexerRepository<Item, Long> {

	Optional<Item> findByName(String name);

}
```

The `ItemRepository` can be injected to other spring-managed beans using `@Autowired` or
any other Spring Framework's approach.

```java
@Autowired
private ItemRepository repository;
```

## Work in progress

- Transactions support using `@Transactional` annotation.
- Binding parameters for a native query using `@Param` annotation.
- Support more return types for `ReindexerRepository`.
