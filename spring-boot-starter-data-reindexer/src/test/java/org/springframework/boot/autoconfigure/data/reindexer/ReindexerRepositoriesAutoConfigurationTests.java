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
package org.springframework.boot.autoconfigure.data.reindexer;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ReindexerContainer;
import org.springframework.boot.autoconfigure.TestAutoConfigurationPackage;
import org.springframework.boot.autoconfigure.data.reindexer.empty.EmptyDataPackage;
import org.springframework.boot.autoconfigure.data.reindexer.person.Person;
import org.springframework.boot.autoconfigure.data.reindexer.person.PersonRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReindexerRepositoriesAutoConfiguration}.
 *
 * @author Evgeniy Cheban
 */
@Testcontainers
class ReindexerRepositoriesAutoConfigurationTests {

	// @formatter:off
	@Container
	static ReindexerContainer reindexer = new ReindexerContainer("test")
            .withSsl("builtin-server.crt", "builtin-server.key");
    // @formatter:on

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ReindexerDataAutoConfiguration.class,
				ReindexerRepositoriesAutoConfiguration.class))
		.withPropertyValues("spring.data.reindexer.urls=" + reindexer.getRpcUrl());

	@Test
	void testDefaultRepositoryConfiguration() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.run((context) -> assertThat(context).hasSingleBean(PersonRepository.class).hasSingleBean(Reindexer.class));
	}

	@Test
	void testNoRepositoryConfiguration() {
		this.contextRunner.withUserConfiguration(EmptyConfiguration.class)
			.run((context) -> assertThat(context).doesNotHaveBean(PersonRepository.class)
				.hasSingleBean(Reindexer.class));
	}

	@Test
	void enablingReactiveRepositoriesDisablesImperativeRepositories() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.data.reindexer.repositories.type=reactive")
			.run((context) -> assertThat(context).doesNotHaveBean(PersonRepository.class));
	}

	@Test
	void enablingNoRepositoriesDisablesImperativeRepositories() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.data.reindexer.repositories.type=none")
			.run((context) -> assertThat(context).doesNotHaveBean(PersonRepository.class));
	}

	@Test
	void enablingSslUsesProvidedKeyStore() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.data.reindexer.urls=" + reindexer.getRpcSslUrl())
			.withPropertyValues("spring.data.reindexer.ssl.enabled=true")
			.withPropertyValues("spring.data.reindexer.ssl.keyStore=classpath:builtin-server.jks")
			.withPropertyValues("spring.data.reindexer.ssl.keyStorePassword=password")
			.run((context) -> assertThat(context).hasSingleBean(PersonRepository.class));
	}

	@Configuration(proxyBeanMethods = false)
	@TestAutoConfigurationPackage(Person.class)
	static class TestConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@TestAutoConfigurationPackage(EmptyDataPackage.class)
	static class EmptyConfiguration {

	}

}
