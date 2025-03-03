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
package org.springframework.data.reindexer.repository.config;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.support.ReindexerRepositoryFactoryBean;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;

/**
 * A {@link RepositoryConfigurationExtension} for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryConfigurationExtension extends RepositoryConfigurationExtensionSupport {

	@Override
	public String getModuleName() {
		return "Reindexer";
	}

	@Override
	protected String getModulePrefix() {
		return "reindexer";
	}

	@Override
	public String getRepositoryFactoryBeanClassName() {
		return ReindexerRepositoryFactoryBean.class.getName();
	}

	@Override
	protected Collection<Class<? extends Annotation>> getIdentifyingAnnotations() {
		return Collections.singleton(Namespace.class);
	}

	@Override
	protected Collection<Class<?>> getIdentifyingTypes() {
		return Collections.singleton(ReindexerRepository.class);
	}

	@Override
	public void postProcess(BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource config) {
		AnnotationAttributes attributes = config.getAttributes();
		builder.addPropertyReference("reindexer", attributes.getString("reindexerRef"));
		builder.addPropertyReference("mappingContext", "reindexerMappingContext");
	}

}
