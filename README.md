Spring Data Reindexer
====================
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.evgeniycheban/spring-data-reindexer/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.evgeniycheban/spring-data-reindexer)

Provides Spring Data approach to work with Reindexer database.
To use with Spring Boot, consider [Spring Boot Starter Data Reindexer](https://github.com/evgeniycheban/spring-data-reindexer/tree/main/spring-boot-starter-data-reindexer).

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
import org.springframework.data.reindexer.repository.config.ReindexerConfigurationSupport;

@Configuration
@EnableReindexerRepositories
public class ReindexerConfig extends ReindexerConfigurationSupport {

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
import org.springframework.data.reindexer.repository.config.ReindexerConfigurationSupport;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableReindexerRepositories
@EnableTransactionManagement
public class TransactionalReindexerConfig extends ReindexerConfigurationSupport {

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

## @Query annotation support
The `@Query` annotation is used to declare SQL-based Reindexer queries
directly on repository methods.

Parameter binding supports named parameters using `@Param` annotation as well
as parameter number links.

```java
@Query("SELECT * FROM items WHERE name = :name")
Optional<Item> findOneByName(@Param("name") String name);

@Query("SELECT * FROM items WHERE name = ?1")
Optional<Item> findOneByName(String name);
```

## Query By Example
Query by Example (QBE) is a user-friendly querying technique with a simple interface.
It allows dynamic query creation and does not require you to write queries that contain
field names. In fact, Query by Example does not require you to write queries by using
query-methods or `@Query` annotation.
```java
Item item = new Item();
item.setName("Test");
NestedItem nestedItem = new NestedItem();
nestedItem.setValue("Value");
item.setNestedItem(nestedItem);
List<Item> items = this.repository.findAll(Example.of(item, ExampleMatcher.matching()
    .withMatcher("name", ExampleMatcher.GenericPropertyMatcher.of(ExampleMatcher.StringMatcher.STARTING))
    .withMatcher("nestedItem.value", ExampleMatcher.GenericPropertyMatcher.of(StringMatcher.ENDING))));
```
More information can be read in [Spring Data reference guide.](https://docs.spring.io/spring-data/relational/reference/query-by-example.html)

## Namespace References
The child-objects can be stored not only within the namespace,
they can also be stored separately using `@NamespaceReference` annotation to refer a child-namespace.
The `@NamespaceReference` annotation has the following attributes to specify how child-namespace is mapped and loaded from Reindexer:
* `namespace (String, optional)` Represents a namespace name to refer, defaults to `@Namespace` entity definition.
* `indexName (String, required)` The index name to be used from parent-namespace to refer to the child-namespace,
typically this index stores a child-namespace `id` value.
* `joinType (JoinType, optional)` The join type to be used to match values in parent and child namespaces, possible values:
  * `JoinType.LEFT (default)` Returns all records from the left (parent) namespace, and the matched records from the right (child) namespace.
  * `JoinType.RIGHT` Returns records that have matching values in both parent and child namespaces.
* `lazy (boolean, optional)` Controls whether the referenced entity should be loaded lazily. This defaults to `false`.
* `fetch (boolean, optional)` Controls whether the referenced entity should be fetched if it is a nested relationship within the child-object of the top level entity. This defaults to `false`.
### Usage example
```java
Long joinedItemId;

Long nestedJoinedItemId;

List<Long> joinedItemIds;

@Transient
@NamespaceReference(indexName = "joinedItemId", joinType = JoinType.INNER)
JoinedItem joinedItem;

@Transient
@NamespaceReference(indexName = "nestedJoinedItemId", fetch = true)
JoinedItem nestedJoinedItem;

@Transient
@NamespaceReference(indexName = "joinedItemIds", lazy = true)
List<JoinedItem> joinedItems;
```
### Implementation notes and limitations:
* The `@Transient` annotation is required to use with `@NamespaceReference` to indicate that Reindexer
should not store child objects in the parent-namespace and therefore those objects should be loaded through
referred `indexName`.
* When the `lazy` attribute is set to `true` the referenced entity is loaded through proxy object.
Depending on a mapped type the framework would create either interface-based (JDK dynamic proxies) or class-based proxies (CGLIB),
`final` classes cannot be proxied since CGLIB relies on creating a subclass for the type being proxied.
When `lazy` attribute is set to `true` the `joinType` attribute is ignored since the object would
be retrieved from Reindexer using `select` query, for single result the query condition would be `Condition.EQ`
and for collection-like result type the query condition would be `Condition.SET` with the value stored in
`indexName` specified in `@NamespaceReference` annotation.
The proxy object is thread-safe meaning that it is safe to access proxy object from multiple threads,
and the initialization of proxy object would be triggered only once.
* In the current implementation there is no option to provide a custom query for
lazy loading namespace reference, however it might be provided it future releases.
* A `JoinType` is only applied to fetch non-lazy child-objects of the top level entity if you need to fetch deeply nested child-objects
like `A - B - C` use `fetch = true` to fetch object `C`, it will be fetched lazily. Reindexer does not support
joins for deeply nested child-objects therefore they can only be loaded using `fetch = true` attribute.

## Projections
Projections allow creating dedicated return types based on certain attributes of domain types.
You can create partial views using interface-based or class-based projections.

The easiest way to limit the result of the queries to only name and value attributes
is by declaring an interface that exposes accessor methods for the properties to be read, as shown in the following example:

A projection interface to retrieve a subset of attributes
```java
interface ItemNameValue {

    String getName();

    String getValue();
}
```
The important bit here is that the properties defined here exactly match properties
in the aggregate root. Doing so lets a query method be added as follows:

A repository using an interface based projection with a query method
```java
interface ItemRepository extends ReindexerRepository<Item, Long> {

    Collection<ItemNameValue> findByName(String name);
}
```
The query execution engine creates proxy instances of that interface at
runtime for each element returned and forwards calls to the exposed methods to the target object.

Projections can be used recursively. If you want to include some of the JoinedItem information as well, create a projection
interface for that and return that interface from the declaration of getJoinedItem(), as shown in the following example:

A projection interface to retrieve a subset of attributes
```java
interface ItemNameValue {

    String getName();

    String getValue();

    JoinedItemName getJoinedItem();

    interface JoinedItemName {

        String getName();
    }
}
```
More information regarding Spring Data Projections and the difference between interface-based, class-based and dynamic projections
can be read in [Spring Data reference guide.](https://docs.spring.io/spring-data/relational/reference/repositories/projections.html)

## CustomConversions
The following example of a Double Converter implementation converts from a Double to a
custom Price value object:
```java
public record Price (double price) {
}

@ReadingConverter
public class PriceReadingConverter implements Converter<Double, Price> {

    public Price convert(Double source) {
        return new Price(source);
    }
}
```
If you write a `Converter` whose source and target types are native types, we cannot determine
whether we should consider it as a reading or a writing converter. Registering the converter
instance as both might lead to unwanted results. For example, a `Converter<String, Long>` is ambiguous,
although it probably does not make sense to try to convert all `String` instances into `Long` instances when writing.
To let you force the infrastructure to register a converter for only one way, we provide `@ReadingConverter` and `@WritingConverter`
annotations to be used in the converter implementation.

Note:
In the current version `@WritingConverter` annotation is not supported, it will be provided in future releases.

Converters are subject to explicit registration as instances are not picked up from a
classpath or container scan to avoid unwanted registration with a conversion service and
the side effects resulting from such a registration. Converters are registered with
`CustomConversions` as the central facility that allows registration and querying for
registered converters based on source and target types.

The following example shows how converters are registered with `CustomConverters`:
```java
@Configuration
@EnableReindexerRepositories
public class ReindexerConfig extends ReindexerConfigurationSupport {

    @Override
    public ReindexerCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new PriceReadingConverter());
        return new ReindexerCustomConversions(StoreConversions.NONE, converters);
    }
}
```
More information can be read in [Spring Data reference guide.](https://docs.spring.io/spring-data/relational/reference/commons/custom-conversions.html)
## Work in progress

- Support more return types for `ReindexerRepository`.
