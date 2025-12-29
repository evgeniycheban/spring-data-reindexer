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
package org.springframework.data.reindexer.repository.aot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import ru.rt.restream.reindexer.Reindexer;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import org.springframework.data.reindexer.repository.support.ReindexerQueryExecutionConverters;
import org.springframework.data.repository.aot.generate.AotRepositoryClassBuilder;
import org.springframework.data.repository.aot.generate.AotRepositoryConstructorBuilder;
import org.springframework.data.repository.aot.generate.MethodContributor;
import org.springframework.data.repository.aot.generate.QueryMetadata;
import org.springframework.data.repository.aot.generate.RepositoryContributor;
import org.springframework.data.repository.config.AotRepositoryContext;
import org.springframework.data.repository.config.AotRepositoryContextSupport;
import org.springframework.data.repository.config.AotRepositoryInformation;
import org.springframework.data.repository.config.RepositoryConfigurationSource;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.repository.util.ReactiveWrapperConverters;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.TypeName;
import org.springframework.util.StringUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerRepositoryContributor extends RepositoryContributor {

	private final String reindexerRef;

	private final ReindexerMappingContext mappingContext;

	/**
	 * Creates an instance.
	 * @param repositoryContext the {@link AotRepositoryContext} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 */
	public ReindexerRepositoryContributor(AotRepositoryContext repositoryContext,
			ReindexerMappingContext mappingContext) {
		super(new ReindexerAotRepositoryContextSupport(repositoryContext));
		this.mappingContext = mappingContext;
		RepositoryConfigurationSource configurationSource = repositoryContext.getConfigurationSource();
		this.reindexerRef = configurationSource.getAttribute("reindexerRef").orElse(null);
	}

	@Override
	protected void customizeClass(AotRepositoryClassBuilder classBuilder) {
		classBuilder
			.customize(builder -> builder.superclass(TypeName.get(ReindexerAotRepositoryFragmentSupport.class)));
	}

	@Override
	protected void customizeConstructor(AotRepositoryConstructorBuilder constructorBuilder) {
		constructorBuilder.addParameter("reindexer", Reindexer.class,
				customizer -> customizer.origin(StringUtils.hasText(this.reindexerRef)
						? new RuntimeBeanReference(this.reindexerRef, Reindexer.class)
						: new RuntimeBeanReference(Reindexer.class)));
		constructorBuilder.addParameter("mappingContext", ReindexerMappingContext.class,
				customizer -> customizer.origin(new RuntimeBeanReference(ReindexerMappingContext.class)));
		constructorBuilder.addParameter("reindexerConverter", ReindexerConverter.class,
				customizer -> customizer.origin(new RuntimeBeanReference(ReindexerConverter.class)));
		constructorBuilder
			.customize(builder -> builder.addStatement("super(reindexer, mappingContext, reindexerConverter, $T.class)",
					getRepositoryInformation().getDomainType()));
	}

	@Override
	protected MethodContributor<? extends QueryMethod> contributeQueryMethod(Method method) {
		ReindexerQueryMethod queryMethod = new ReindexerQueryMethod(method, getRepositoryInformation(),
				getProjectionFactory());
		if ("query".equals(method.getName())) {
			return null;
		}
		if (queryMethod.hasQueryAnnotation()) {
			// TODO: To be implemented in gh-93
			return null;
		}
		QueryMetadata queryMetadata = Map::of;
		return MethodContributor.forQueryMethod(queryMethod).withMetadata(queryMetadata).contribute(context -> {
			CodeBlock.Builder body = CodeBlock.builder();
			PartTree tree = new PartTree(queryMethod.getName(), queryMethod.getDomainClass());
			body.add(ReindexerCodeBlocks.derivedQueryCodeBlockBuilder(tree, context, this.mappingContext).build());
			body.add(";\n");
			body.add(ReindexerCodeBlocks
				.derivedExecutionCodeBlockBuilder(tree, context, this.mappingContext, queryMethod)
				.build());
			return body.build();
		});
	}

	private static final class ReindexerAotRepositoryContextSupport extends AotRepositoryContextSupport {

		private final AotRepositoryContext delegate;

		private final ReindexerAotRepositoryInformation repositoryInformation;

		private ReindexerAotRepositoryContextSupport(AotRepositoryContext delegate) {
			super(delegate);
			this.delegate = delegate;
			this.repositoryInformation = new ReindexerAotRepositoryInformation(delegate.getRepositoryInformation());
		}

		@Override
		public String getModuleName() {
			return this.delegate.getModuleName();
		}

		@Override
		public RepositoryConfigurationSource getConfigurationSource() {
			return this.delegate.getConfigurationSource();
		}

		@Override
		public Collection<Class<? extends Annotation>> getIdentifyingAnnotations() {
			return this.delegate.getIdentifyingAnnotations();
		}

		@Override
		public Set<MergedAnnotation<Annotation>> getResolvedAnnotations() {
			return this.delegate.getResolvedAnnotations();
		}

		@Override
		public Set<Class<?>> getResolvedTypes() {
			return this.delegate.getResolvedTypes();
		}

		@Override
		public RepositoryInformation getRepositoryInformation() {
			return this.repositoryInformation;
		}

	}

	private static final class ReindexerAotRepositoryInformation extends AotRepositoryInformation {

		private ReindexerAotRepositoryInformation(RepositoryInformation information) {
			super(information, information.getRepositoryBaseClass(), information.getFragments());
		}

		@Override
		public Class<?> getReturnedDomainClass(Method method) {
			TypeInformation<?> returnType = getReturnType(method);
			returnType = ReactiveWrapperConverters.unwrapWrapperTypes(returnType);
			return ReindexerQueryExecutionConverters.unwrapWrapperTypes(returnType, getDomainTypeInformation())
				.getType();
		}

	}

}
