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
import org.mockito.Mockito;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;
import ru.rt.restream.reindexer.binding.cproto.DataSourceFactoryStrategy;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ReindexerContainer;
import org.springframework.boot.autoconfigure.data.reindexer.person.Person;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.ManagedTypes;
import org.springframework.data.reindexer.core.convert.MappingReindexerConverter;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.convert.ReindexerCustomConversions;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReindexerDataAutoConfiguration}.
 *
 * @author Evgeniy Cheban
 */
@Testcontainers
class ReindexerDataAutoConfigurationTests {

	@Container
	static ReindexerContainer reindexerContainer = new ReindexerContainer("test");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ReindexerDataAutoConfiguration.class))
		.withPropertyValues("spring.data.reindexer.urls=" + reindexerContainer.getRpcUrl());

	@Test
	void testDefaultAutoConfiguration() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(Reindexer.class)
			.hasSingleBean(ReindexerConfiguration.class)
			.hasSingleBean(ManagedTypes.class)
			.hasSingleBean(MappingReindexerConverter.class)
			.hasSingleBean(ReindexerCustomConversions.class)
			.hasSingleBean(ReindexerMappingContext.class));
	}

	@Test
	void testOverrideManagedTypesAutoConfiguration() {
		ManagedTypes managedTypes = ManagedTypes.from(Person.class);
		this.contextRunner.withBean(ManagedTypes.class, () -> managedTypes)
			.run(context -> assertThat(context).getBean(ManagedTypes.class).isSameAs(managedTypes));
	}

	@Test
	void testOverrideReindexerAutoConfiguration() {
		Reindexer reindexer = ReindexerConfiguration.builder().url(reindexerContainer.getRpcUrl()).getReindexer();
		this.contextRunner.withBean(Reindexer.class, () -> reindexer)
			.run(context -> assertThat(context).getBean(Reindexer.class).isSameAs(reindexer));
	}

	@Test
	void testOverrideReindexerConfigurationAutoConfiguration() {
		ReindexerConfiguration reindexerConfiguration = ReindexerConfiguration.builder()
			.url(reindexerContainer.getRpcUrl());
		this.contextRunner.withBean(ReindexerConfiguration.class, () -> reindexerConfiguration)
			.run(context -> assertThat(context).getBean(ReindexerConfiguration.class).isSameAs(reindexerConfiguration));
	}

	@Test
	void testOverrideReindexerConverterAutoConfiguration() {
		ReindexerConverter reindexerConverter = Mockito.mock(ReindexerConverter.class);
		this.contextRunner.withBean(ReindexerConverter.class, () -> reindexerConverter).run(context -> {
			assertThat(context).getBean(ReindexerConverter.class).isSameAs(reindexerConverter);
			assertThat(context).doesNotHaveBean(MappingReindexerConverter.class);
		});
	}

	@Test
	void testOverrideReindexerCustomConversionsAutoConfiguration() {
		ReindexerCustomConversions conversions = new ReindexerCustomConversions();
		this.contextRunner.withBean(ReindexerCustomConversions.class, () -> conversions)
			.run(context -> assertThat(context).getBean(ReindexerCustomConversions.class).isSameAs(conversions));
	}

	@Test
	void testOverrideReindexerMappingContextAutoConfiguration() {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		this.contextRunner.withBean(ReindexerMappingContext.class, () -> mappingContext)
			.run(context -> assertThat(context).getBean(ReindexerMappingContext.class).isSameAs(mappingContext));
	}

	@Test
	void testOverrideDataSourceFactoryFromStringAutoConfiguration() {
		this.contextRunner.withPropertyValues("spring.data.reindexer.data-source-factory=random")
			.run(context -> assertThat(context).getBean(ReindexerProperties.class)
				.extracting(ReindexerProperties::getDataSourceFactory)
				.isEqualTo(DataSourceFactoryStrategy.RANDOM));
	}

}
