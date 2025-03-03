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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for Spring Data Reindexer to be extended for JavaConfiguration usage.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public abstract class ReindexerConfigurationSupport {

	@Bean
	public ReindexerMappingContext reindexerMappingContext() throws ClassNotFoundException {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		mappingContext.setInitialEntitySet(getInitialEntitySet());
		return mappingContext;
	}

	/**
	 * Scans the mapping base package for classes annotated with {@link Namespace}. By default, it scans for entities in
	 * all packages returned by {@link #getMappingBasePackages()}.
	 *
	 * @see #getMappingBasePackages()
	 * @return a set of entities
	 * @throws ClassNotFoundException if the class could not be loaded
	 */
	protected Set<Class<?>> getInitialEntitySet() throws ClassNotFoundException {
		Set<Class<?>> initialEntitySet = new HashSet<>();
		for (String basePackage : getMappingBasePackages()) {
			initialEntitySet.addAll(scanForEntities(basePackage));
		}
		return initialEntitySet;
	}

	/**
	 * Returns the base packages to scan for Reindexer mapped entities at startup. Will return the package name of the
	 * configuration class' (the concrete class, not this one here) by default. So if you have a
	 * {@code com.acme.AppConfig} extending {@link ReindexerConfigurationSupport} the base package will be considered
	 * {@code com.acme} unless the method is overridden to implement alternate behavior.
	 *
	 * @return the base packages to scan for mapped {@link Namespace} classes or an empty collection to not enable scanning
	 *         for entities
	 */
	protected Collection<String> getMappingBasePackages() {
		Package mappingBasePackage = getClass().getPackage();
		return Collections.singleton(mappingBasePackage == null ? null : mappingBasePackage.getName());
	}

	/**
	 * Scans the given base package for entities, i.e. Reindexer specific types annotated with {@link Namespace}.
	 *
	 * @param basePackage must not be {@literal null}
	 * @return a set of entities
	 * @throws ClassNotFoundException if the class could not be loaded
	 */
	protected Set<Class<?>> scanForEntities(String basePackage) throws ClassNotFoundException {
		if (!StringUtils.hasText(basePackage)) {
			return Collections.emptySet();
		}
		Set<Class<?>> initialEntitySet = new HashSet<>();
		if (StringUtils.hasText(basePackage)) {
			ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(
					false);
			componentProvider.addIncludeFilter(new AnnotationTypeFilter(Namespace.class));
			for (BeanDefinition candidate : componentProvider.findCandidateComponents(basePackage)) {
				initialEntitySet
						.add(ClassUtils.forName(candidate.getBeanClassName(), ReindexerConfigurationSupport.class.getClassLoader()));
			}
		}
		return initialEntitySet;
	}

}
