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
package org.springframework.data.reindexer.repository.support;

import java.io.Serializable;

import ru.rt.restream.reindexer.Reindexer;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

/**
 * A {@link FactoryBean} to create {@link ReindexerRepository} instances.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
		extends RepositoryFactoryBeanSupport<T, S, ID> implements ApplicationContextAware {

	private Reindexer reindexer;

	private ReindexerMappingContext mappingContext;

	private ReindexerConverter reindexerConverter;

	private ApplicationContext ctx;

	/**
	 * Creates an instance.
	 *
	 * @param repositoryInterface the repository interface to use
	 */
	public ReindexerRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
		super(repositoryInterface);
	}

	/**
	 * Sets the {@link Reindexer}.
	 *
	 * @param reindexer the {@link Reindexer} to use
	 */
	public void setReindexer(Reindexer reindexer) {
		this.reindexer = reindexer;
	}

	/**
	 * Sets the {@link ReindexerMappingContext}.
	 *
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 */
	public void setMappingContext(ReindexerMappingContext mappingContext) {
		this.mappingContext = mappingContext;
	}

	/**
	 * Sets the {@link ReindexerConverter}.
	 *
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 * @since 1.4
	 */
	public void setReindexerConverter(ReindexerConverter reindexerConverter) {
		this.reindexerConverter = reindexerConverter;
	}

	@Override
	protected RepositoryFactorySupport createRepositoryFactory() {
		return new ReindexerRepositoryFactory(this.reindexer, this.mappingContext, this.reindexerConverter, this.ctx);
	}

	@Override
	public void afterPropertiesSet() {
		Assert.state(this.reindexer != null,
				"Reindexer instance is not configured. Consider add Reindexer @Bean to the ApplicationContext");
		super.afterPropertiesSet();
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		this.ctx = ctx;
	}
}
