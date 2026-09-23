Spring Data Reindexer
====================
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/io.github.evgeniycheban/spring-data-reindexer/badge.svg)](https://maven-badges.sml.io/sonatype-central/io.github.evgeniycheban/spring-data-reindexer/)

Spring Data module for the Reindexer database. Provides repositories, object mapping, query derivation,
type-safe string queries, projections, namespace references, transactions, custom conversions, and AOT support.

# Table of contents:

- [Usage](#usage)
  - [Maven](#maven)
  - [application.properties](#applicationproperties)
  - [Configuration](#configuration)
  - [Entity](#entity)
  - [Repository](#repository)
- [Query annotation support](#query-annotation-support)
  - [Basic usage](#basic-query-annotation-usage)
  - [Advanced Query annotation support with extra type-safety using JSQLParser](#advanced-query-annotation-support-with-extra-type-safety-using-jsqlparser)
- [Transactions](#transactions)
  - [Supported propagation levels](#supported-propagation-levels)
  - [Readonly transactions](#readonly-transactions)
  - [Transactional configuration](#transactional-configuration)
  - [Transactional Service](#transactional-service)
- [Query By Example](#query-by-example)
- [Namespace References](#namespace-references)
  - [Usage example](#namespace-reference-usage-example)
  - [Implementation notes and limitations](#namespace-reference-implementation-notes-and-limitations)
- [Projections](#projections)
- [Custom conversions](#custom-conversions)
- [AOT (Ahead of Time) optimizations](#aot-ahead-of-time-optimizations)
  - [AOT Spring Boot Maven Plugin configuration](#aot-spring-boot-maven-plugin-configuration)
  - [AOT application.properties](#aot-applicationproperties)
  - [AOT usage example](#aot-usage-example)
- [License](#license)

## Usage

### Maven

```xml

<dependency>
	<groupId>io.github.evgeniycheban</groupId>
	<artifactId>spring-data-reindexer</artifactId>
	<version>${spring-data-reindexer.version}</version>
</dependency>
```

To use with Spring Boot, consider Spring Boot Starter Data Reindexer:

```xml

<dependency>
	<groupId>io.github.evgeniycheban</groupId>
	<artifactId>spring-boot-starter-data-reindexer</artifactId>
	<version>${spring-boot-starter-data-reindexer.version}</version>
</dependency>
```

### application.properties

A minimal configuration example using Spring Boot:
```properties
spring.application.name=demo
# Reindexer url(s) to connect, defaults to cproto://localhost:6534/test
spring.data.reindexer.urls=cproto://localhost:6534/demo
# Other properties can be found in org.springframework.boot.autoconfigure.data.reindexer.ReindexerProperties class
# with the description that is also available from your favorite IDE.
```
See the configuration example below for using Spring Data Reindexer without Spring Boot.

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

import org.springframework.data.annotation.Id;
import org.springframework.data.reindexer.core.mapping.Namespace;

@Namespace(name = "items")
public class Item {

	@Id
	private Long id;

	@Reindex(name = "name")
	private String name;

	// getters/setters.

}
```
The `@Id` annotation can be used as a shortcut for `@Reindex(name = "id", isPrimaryKey = true)` to specify the primary
key of the entity, it will automatically create a primary-key index if none exists.

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
If `@Reindex` annotation is not defined, indexes used within the derived query method are created automatically when
`@EnableReindexerRepositories#createIndexesForQueryMethods` is `true`.  
The `@Collation` annotation can be used to specify the collation for the index created within the derived query method.

**Note that once the index is created, it cannot be modified, therefore, the `@Collation` annotation is applied only to
the first usage of the index within the derived query method.  
More information can be read in Javadoc of `@Collation` annotation.**

The `ItemRepository` can be injected into spring-managed beans using `@Autowired` or
another Spring Framework approach.

```java
@Autowired
private ItemRepository repository;
```

## Transactions

Transactions are implemented using `@Transactional` annotation approach, to enable
transaction management support `@EnableTransactionManagement` annotation should be placed
on the `@Configuration` class and `ReindexerTransactionManager` bean should be defined in
the context.

**Note that `ReindexerTransactionManager` manages transactions for a single namespace, therefore, it should be defined
with a domain class that is mapped to a Reindexer namespace.**

### Supported propagation levels
The `ReindexerTransactionManager` supports the following transaction propagation levels:
* `REQUIRED` (default) Support a current transaction, create a new one if none exists.
* `REQUIRES_NEW` Create a new transaction, and suspend the current transaction if one exists.

**Note: `NESTED` is not supported as there is no native support for nested transactions in Reindexer.**

### Readonly transactions
The `ReindexerTransactionManager` supports readonly transactions using the `readOnly` attribute.  
If the `readOnly` attribute is set to `true`, the transaction is marked as read-only and executing
a write operation will result in an exception.

Here is an example of basic transaction management usage:

### Transactional configuration

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

Parameter binding supports named parameters as well as parameter references using `?1` style placeholders.

### Basic @Query annotation usage

```java
@Query("SELECT * FROM items WHERE name = :name")
Optional<Item> findOneByName(@Param("name") String name);

@Query("SELECT * FROM items WHERE name = :name")
Optional<Item> findOneByName(String name);

@Query("SELECT * FROM items WHERE name = ?1")
Optional<Item> findOneByName(String name);
```

### Advanced @Query annotation support with extra type-safety using `JSQLParser`.
The `@Query` annotation supports `JSQLParser` to provide type-safe query bindings.  
To enable `JSQLParser` support, it should be added to the classpath:
```xml
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>5.4</version>
</dependency>
```
When `JSQLParser` is available on the classpath, the `@Query` annotation support infrastructure will parse and validate
the query before execution, providing type-safe bindings, and parameter conversion.  
The provided query must be compliant with the ANSI SQL standard, the Reindexer specific functions e.g., `KNN`, `RANGE`
are available as SQL functions, for example:
```java
@Query("SELECT *, vectors(), rank() FROM item_float_vectors WHERE knn(float_vector_index, :vector, :knnSearchParam)")
List<ItemFloatVector> findAllByVectorIndex(Vector vector, KnnSearchParam knnSearchParam);
```
**Note that for `vector` parameter, either `float[]` or `org.springframework.data.domain.Vector` can be used.**

The `RANGE` can be used as a standard SQL function:
```java
@Query("SELECT * FROM items WHERE range(defaultDate, :start, :end)")
List<Item> findAllByDefaultDateBetween(LocalDate start, LocalDate end);
```
Additionally, JPQL-like query syntax is supported, for example:
```java
@Query("select it from Item it where it.name = :name")
Optional<Item> findByName(String name);
```
To force a certain query to execute a native Reindexer query, use the `nativeQuery=true` attribute.  
**See [AOT (Ahead of Time) optimizations](#aot-ahead-of-time-optimizations) section for more information about
`JSQLParser` support within the AOT scenario.**

To execute a modifying query, use the `update=true` attribute:
```java
@Query(value = "UPDATE items SET name = ?1 WHERE id = ?2", update = true)
void updateNameSql(String name, Long id);
```

More information can be read in Javadoc of `@Query` annotation.

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
The child objects can be stored not only within the namespace,
they can also be stored separately using `@NamespaceReference` annotation to refer a child namespace.
The `@NamespaceReference` annotation has the following attributes to specify how child namespace is mapped and loaded from Reindexer:
- `namespace (String, optional)` Represents a namespace name to refer, defaults to `@Namespace` entity definition.
- `indexName (String, required)` The index name to be used from the parent namespace to refer to the child namespace,
typically this index stores a child namespace `id` value.
- `joinType (JoinType, optional)` The join type to be used to match values in parent and child namespaces, possible values:
  - `JoinType.LEFT (default)` Returns all records from the left (parent) namespace, and the matched records from the right (child) namespace.
  - `JoinType.INNER` Returns records that have matching values in both parent and child namespaces.
- `lazy (boolean, optional)` Controls whether the referenced entity should be loaded lazily. This defaults to `false`.
- `fetch (boolean, optional)` Controls whether the referenced entity should be fetched if it is a nested relationship
within the child object of the top level entity. This defaults to `false`.  
**Deprecated since the `1.7` release, and Reindexer server version >= `5.16.0`, Reindexer provides native support for
nested joins. Self-joins are fetched lazily using proxies.**
- `lookup (string, optional)` Defines a custom lookup query to fetch namespace reference. The query can contain a
SpEL expression that refers to application or aggregate root's context:  
`select * from joined_items where id in #{joinedItemIds} order by id desc`  
Alternatively, you can use SpEL expression to fetch namespace reference by calling
a spring-managed bean, for example, you can directly call repository method to
retrieve necessary data:  
`#{@joinedItemRepository.findAllById(joinedItemIds)}}`
- `sort (string, optional)` Defines a specific sort orders to be applied to the target query.  
Example: `id asc, name desc, value`, default direction is `asc`.  
If the `lookup` query defines `ORDER BY` clause, the sort attribute can be accessed using `#sortString` variable.  
You can use the sort object in the SpEL expression by using reference `#sort` to
access it, the target type of sort object is `org.springframework.data.domain.Sort` this object can be passed to the
method invocation within the expression:  
`#{@joinedItemRepository.findAllById(joinedItemIds, #sort)}}`  
More information can be read in Javadoc of `@NamespaceReference` annotation.

### Namespace reference usage example
```java
Long joinedItemId;

Long nestedJoinedItemId;

List<Long> joinedItemIds;

@Transient
@NamespaceReference(indexName = "joinedItemId", joinType = JoinType.INNER)
JoinedItem joinedItem;

// Since the 1.7 release, the fetch attribute is no longer required to fetch nested joins
// if the Reindexer server version is >= 5.16.0
@Transient
@NamespaceReference(indexName = "nestedJoinedItemId", fetch = true)
JoinedItem nestedJoinedItem;

@Transient
@NamespaceReference(indexName = "joinedItemIds", lazy = true)
List<JoinedItem> joinedItems;

// The sort attribute is applied to the lookup query
@Transient
@NamespaceReference(lookup = """
            select *
              from joined_items
             where id in (#{joinedItemIds})
             order by
                   price desc,
                   name asc,
                   #{#sortString}
             limit 10
        """, sort = "value, id asc")
List<JoinedItem> joinedItemsLookup;
```
**Important:** Fields annotated with `@NamespaceReference` must also be annotated with
`@ru.rt.restream.reindexer.annotations.Transient`. This tells Reindexer not to persist the referenced object in the
parent namespace. Instead, the object is resolved through a namespace reference.

### Namespace reference implementation notes and limitations
- When the `lazy` attribute is set to `true`, the referenced entity is loaded through the proxy object.
Depending on the mapped type, the framework creates either interface-based (JDK dynamic proxies) or class-based proxies (CGLIB),
`final` classes cannot be proxied since CGLIB relies on creating a subclass for the type being proxied.
When the `lazy` attribute is set to `true` the `joinType` attribute is ignored since the object is retrieved from Reindexer
using a `select` query, for a single result the query condition is `Condition.EQ` and for a collection-like result type
the query condition is `Condition.SET` with the value stored in `indexName` specified in `@NamespaceReference` annotation.
The proxy object is thread-safe, meaning that it is safe to access a proxy object from multiple threads,
and the initialization of a proxy object is triggered only once.
- When using query format v1 (Reindexer server version < 5.16.0), `JoinType` is only applied to fetch non-lazy
child objects of the top-level entity if you need to fetch deeply nested child objects
like `A - B - C` use `fetch = true` to fetch object `C`, it will be fetched lazily.  
Since the `1.7` release, and Reindexer server version >= `5.16.0`, Reindexer provides native support for nested joins,
therefore, `fetch` attribute is no longer required to fetch deeply nested child objects. The self-joins are fetched
lazily using proxies. Query format version is negotiated from the Reindexer server, and can be explicitly configured
using `ReindexerMappingContext#setQueryFormatVersion(Supplier<Integer>)`.  
**See [AOT (Ahead of Time) optimizations](#aot-ahead-of-time-optimizations) section for more information about
configuring a query format version in the AOT scenario.**

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
More information regarding Spring Data Projections and the difference between interface-based, class-based, and dynamic projections
can be read in [Spring Data reference guide.](https://docs.spring.io/spring-data/relational/reference/repositories/projections.html)

## Custom conversions
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
and vice versa:
```java
@WritingConverter
public class PriceWritingConverter implements Converter<Price, Double> {

    public Double convert(Price source) {
        return source.price();
    }

}
```
If you write a `Converter` whose source and target types are native types, we cannot determine
whether we should consider it as a reading or a writing converter. Registering the converter
instance as both might lead to unwanted results. For example, a `Converter<String, Long>` is ambiguous,
although it probably does not make sense to try to convert all `String` instances into `Long` instances when writing.
To let you force the infrastructure to register a converter for only one way, we provide `@ReadingConverter` and `@WritingConverter`
annotations to be used in the converter implementation.

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

## AOT (Ahead-of-Time) optimizations
Spring Data Reindexer supports AOT (Ahead-of-Time) optimizations.  
When using Spring Boot, add the following configuration for `spring-boot-maven-plugin`:

### AOT Spring Boot Maven Plugin configuration
```xml
<plugin>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-maven-plugin</artifactId>
	<executions>
		<execution>
			<id>process-aot</id>
			<goals>
				<goal>process-aot</goal>
			</goals>
		</execution>
	</executions>
	<configuration>
		<compilerArguments>
			-parameters
		</compilerArguments>
	</configuration>
</plugin>
```
To enable AOT processing, add `spring.aot.enabled=true` to `application.properties`.

### AOT application.properties
```properties
spring.aot.enabled=true

# Optionally, enable the query format v2, when using Reindexer server version >= 5.16.0 for nested joins support.
spring.data.reindexer.query-format-version=2
```

By default, derived queries are generated using query format v1, to override the default query format version, use
`spring.data.reindexer.query-format-version` property. The query format version cannot be negotiated from the Reindexer
server during the compilation phase, therefore, the query format version must be explicitly configured.  
The generated AOT sources and repository metadata are created in `spring-aot` directory. 

### AOT usage example
Consider the following derived query method:
```java
Optional<ItemProjection> findProjectionByName(String name);
```
it will be compiled by the AOT infrastructure into:
```java
/**
 * AOT generated implementation of {@link ItemReindexerRepository#findProjectionByName(java.lang.String)}.
 */
public Optional<ItemProjection> findProjectionByName(String name) {
	Query<Item> root = query(Item.class)
			.select("id", "name", "value", "lazyJoinedItemId")
			.leftJoin(query(JoinedItem.class)
						.innerJoin(query(NestedJoinedItem.class)
							.on("nestedJoinedItemId", Query.Condition.EQ, "id"), "nestedJoinedItem")
				.on("joinedItemId", Query.Condition.EQ, "id"), "joinedItem")
			.where("name", Query.Condition.EQ, mapParameterValue("name", name));
	ProjectingResultIterator<ItemProjection> it = new ProjectingResultIterator<>(root.execute(), ItemProjection.class,
		Item.class, getReindexerConverter());
	return Optional.ofNullable(ReindexerQueryExecutions.toEntity(it));
}
```
and the metadata will be created in `spring-aot/resources/*`
```json
{
	"name": "com.example.ItemRepository",
	"module": "Reindexer",
	"type": "IMPERATIVE",
	"methods": [
		{
			"name": "findProjectionByName",
			"signature": "public abstract java.util.Optional<com.example.ItemProjection> com.example.ItemRepository.findByName(java.lang.String)",
			"query": {
				"queryFormatVersion": 2,
				"query": "SELECT id, name, value, lazyJoinedItemId FROM items LEFT JOIN (SELECT id, name, value, lazyNestedJoinedItemId FROM joined_items INNER JOIN nested_joined_items ON nested_joined_items.id = joined_items.nested_joined_item_id) ON items.joinedItemId = joined_items.id WHERE name = :name"
			}
		}
	]
}
```
See [the article how IntelliJ IDEA uses AOT metadata to display queries](https://blog.jetbrains.com/idea/2025/11/spring-data-aot)

**Note: when `JSQLParser` is used in AOT mode, the query generation infrastructure will fall back to using a standard
(reflection-based) flow for requests that require `JSQLParser` processing, therefore, only derived and native queries
are processed in AOT mode.**

More information about AOT processing can be read in [Spring reference guide](https://docs.spring.io/spring-framework/reference/core/aot.html)

## License
Spring Data Reindexer is Open Source software released under the
[Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
