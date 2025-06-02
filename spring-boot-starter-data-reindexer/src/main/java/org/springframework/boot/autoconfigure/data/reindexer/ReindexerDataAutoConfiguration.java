/*
 * Copyright 2022 evgeniycheban
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.autoconfigure.data.reindexer;

import java.util.Locale;

import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;
import ru.rt.restream.reindexer.binding.cproto.DataSourceFactory;
import ru.rt.restream.reindexer.binding.cproto.DataSourceFactoryStrategy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScanner;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.ManagedTypes;
import org.springframework.data.reindexer.core.convert.MappingReindexerConverter;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.convert.ReindexerCustomConversions;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for Spring Data's reindexer support.
 *
 * @author Evgeniy Cheban
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnClass({ Reindexer.class, ReindexerRepository.class })
@EnableConfigurationProperties(ReindexerProperties.class)
public class ReindexerDataAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	static ManagedTypes managedTypes(ApplicationContext applicationContext) throws ClassNotFoundException {
		return ManagedTypes.fromIterable(new EntityScanner(applicationContext).scan(Namespace.class));
	}

	@Bean
	@ConditionalOnMissingBean
	Reindexer reindexer(ReindexerConfiguration reindexerConfiguration) {
		return reindexerConfiguration.getReindexer();
	}

	@Bean
	@ConditionalOnMissingBean
	ReindexerConfiguration reindexerConfiguration(ReindexerProperties properties,
			ReindexerCustomConversions conversions, ReindexerMappingContext context) {
		return ReindexerConfiguration.builder()
			.urls(properties.getUrls())
			.allowUnlistedDataSource(properties.isAllowUnlistedDataSource())
			.dataSourceFactory(properties.getDataSourceFactory())
			.connectionPoolSize(properties.getConnectionPoolSize())
			.requestTimeout(properties.getRequestTimeout())
			.serverStartupTimeout(properties.getServerStartupTimeout())
			.serverConfigFile(properties.getServerConfigFile())
			.fieldConverterRegistry(registry -> conversions.registerCustomConversions(registry, context));
	}

	@Bean
	@ConditionalOnMissingBean(ReindexerConverter.class)
	MappingReindexerConverter reindexerConverter(Reindexer reindexer, ReindexerMappingContext context,
			ReindexerCustomConversions conversions) {
		MappingReindexerConverter converter = new MappingReindexerConverter(reindexer, context);
		converter.setConversions(conversions);
		return converter;
	}

	@Bean
	@ConditionalOnMissingBean
	ReindexerCustomConversions customConversions() {
		return new ReindexerCustomConversions();
	}

	@Bean
	@ConditionalOnMissingBean
	ReindexerMappingContext reindexerMappingContext(ManagedTypes mappedTypes, ReindexerCustomConversions conversions) {
		ReindexerMappingContext context = new ReindexerMappingContext();
		context.setManagedTypes(mappedTypes);
		context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		return context;
	}

	@Bean
	@ConfigurationPropertiesBinding
	Converter<String, DataSourceFactory> stringToDataSourceFactoryConverter() {
		return new StringToDataSourceFactoryConverter();
	}

	private static final class StringToDataSourceFactoryConverter implements Converter<String, DataSourceFactory> {

		@Override
		public DataSourceFactory convert(String strategyName) {
			if (StringUtils.hasText(strategyName)) {
				return DataSourceFactoryStrategy.valueOf(strategyName.toUpperCase(Locale.ROOT));
			}
			return null;
		}

	}

}
