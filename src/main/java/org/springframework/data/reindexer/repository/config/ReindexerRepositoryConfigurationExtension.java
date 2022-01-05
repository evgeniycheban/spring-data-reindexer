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
	}

}
