package org.springframework.data.reindexer.repository.config;

import java.lang.annotation.Annotation;

import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

/**
 * Reindexer-specific {@link ImportBeanDefinitionRegistrar}.
 *
 * @author Evgeniy Cheban
 */
class ReindexerRepositoriesRegistrar extends RepositoryBeanDefinitionRegistrarSupport {

	@Override
	protected Class<? extends Annotation> getAnnotation() {
		return EnableReindexerRepositories.class;
	}

	@Override
	protected RepositoryConfigurationExtension getExtension() {
		return new ReindexerRepositoryConfigurationExtension();
	}

}
