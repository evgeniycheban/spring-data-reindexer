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
package org.springframework.data.reindexer.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.binding.Binding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.reindexer.ReindexerTransactionManager;
import org.springframework.data.reindexer.container.ReindexerTestContainer;
import org.springframework.data.reindexer.core.convert.ReindexerCustomConversions;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.data.reindexer.repository.config.ReindexerConfigurationSupport;
import org.springframework.data.reindexer.repository.item.converter.PriceReadingConverter;
import org.springframework.data.reindexer.repository.item.converter.PriceWritingConverter;
import org.springframework.data.reindexer.repository.item.converter.PlaceReadingConverter;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Base class for Reindexer tests.
 *
 * @author Evgeniy Cheban
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
public abstract class AbstractReindexerTest {

	@Autowired
	ClearDbReindexer reindexer;

	@AfterEach
	void tearDown() {
		reindexer.clear();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableReindexerRepositories(basePackageClasses = AbstractReindexerTest.class, considerNestedRepositories = true)
	@EnableTransactionManagement
	@ComponentScan(basePackageClasses = AbstractReindexerTest.class)
	static class ReindexerTestConfig extends ReindexerConfigurationSupport {

		@Bean
		Reindexer reindexer(ReindexerCustomConversions conversions, ReindexerMappingContext context) {
			return new ClearDbReindexer(ReindexerConfiguration.builder()
				.url(ReindexerTestContainer.getCprotoUrl())
				.requestTimeout(Duration.ofSeconds(5))
				.fieldConverterRegistry(registry -> conversions.registerCustomConversions(registry, context))
				.getReindexer()
				.getBinding());
		}

		@Bean
		ReindexerTransactionManager<TestItem> txManager(Reindexer reindexer, ReindexerMappingContext mappingContext) {
			return new ReindexerTransactionManager<>(reindexer, mappingContext, TestItem.class);
		}

		@Override
		public @NonNull ReindexerCustomConversions customConversions() {
			List<Converter<?, ?>> converters = new ArrayList<>();
			converters.add(new PlaceReadingConverter());
			converters.add(new PriceReadingConverter());
			converters.add(new PriceWritingConverter());
			return new ReindexerCustomConversions(converters);
		}

		@Override
		protected boolean autoIndexCreation() {
			return true;
		}

	}

	static class ClearDbReindexer extends Reindexer {

		ClearDbReindexer(Binding binding) {
			super(binding);
		}

		/**
		 * Clears all registered namespaces.
		 */
		void clear() {
			for (ReindexerNamespace<?> namespace : namespaceMap.values()) {
				// Skip system namespaces.
				if (!namespace.getName().startsWith("#")) {
					query(namespace.getName(), namespace.getItemClass()).delete();
				}
			}
		}

	}

}
