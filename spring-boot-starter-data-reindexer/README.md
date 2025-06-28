Spring Boot Starter Data Reindexer
====================
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.evgeniycheban/spring-boot-starter-data-reindexer/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.evgeniycheban/spring-boot-starter-data-reindexer)

Starter for using Spring Data Reindexer.

## Maven

```xml

<dependency>
	<groupId>io.github.evgeniycheban</groupId>
	<artifactId>spring-boot-starter-data-reindexer</artifactId>
	<version>${spring-boot-starter-data-reindexer.version}</version>
</dependency>
```

## Usage

A basic sample of using `spring-boot-starter-data-reindexer`:

### application.properties

```properties
spring.application.name=demo
# Reindexer url(s) to connect, defaults to cproto://localhost:6534/test
spring.data.reindexer.urls=cproto://localhost:6534/demo
# Other properties can be found in org.springframework.boot.autoconfigure.data.reindexer.ReindexerProperties class
# with the description that is also available from your IDE.
```
