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
package org.springframework.data.reindexer.repository.config;

import java.lang.annotation.Annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.data.auditing.config.AuditingBeanDefinitionRegistrarSupport;
import org.springframework.data.auditing.config.AuditingConfiguration;
import org.springframework.data.config.ParsingUtils;
import org.springframework.data.reindexer.core.mapping.event.AuditingEntityCallback;

/**
 * An {@link org.springframework.context.annotation.ImportBeanDefinitionRegistrar} to
 * enable {@link EnableReindexerAuditing} annotation.
 *
 * @author Evgeniy Cheban
 */
class ReindexerAuditingRegistrar extends AuditingBeanDefinitionRegistrarSupport implements Ordered {

	private static final String AUDITING_HANDLER_BEAN_NAME = "reindexerAuditingHandler";

	@Override
	protected Class<? extends Annotation> getAnnotation() {
		return EnableReindexerAuditing.class;
	}

	@Override
	protected String getAuditingHandlerBeanName() {
		return AUDITING_HANDLER_BEAN_NAME;
	}

	@Override
	protected void postProcess(BeanDefinitionBuilder builder, AuditingConfiguration configuration,
			BeanDefinitionRegistry registry) {
		builder.setFactoryMethod("from").addConstructorArgReference("reindexerMappingContext");
	}

	@Override
	protected BeanDefinitionBuilder getAuditHandlerBeanDefinitionBuilder(AuditingConfiguration configuration) {
		return configureDefaultAuditHandlerAttributes(configuration,
				BeanDefinitionBuilder.rootBeanDefinition(IsNewAwareAuditingHandler.class));
	}

	@Override
	protected void registerAuditListenerBeanDefinition(BeanDefinition auditingHandlerDefinition,
			BeanDefinitionRegistry registry) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
			.rootBeanDefinition(AuditingEntityCallback.class);
		beanDefinitionBuilder
			.addConstructorArgValue(ParsingUtils.getObjectFactoryBeanDefinition(AUDITING_HANDLER_BEAN_NAME, registry));
		registerInfrastructureBeanWithId(beanDefinitionBuilder.getBeanDefinition(),
				AuditingEntityCallback.class.getName(), registry);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
