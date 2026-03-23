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
package org.springframework.data.reindexer.repository.aot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import org.springframework.data.reindexer.repository.support.ReindexerDefaultRepositoryMetadata;
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
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.util.Lazy;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.TypeName;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
public final class ReindexerRepositoryContributor extends RepositoryContributor {

	private static final boolean USE_VISITOR_BASED_QUERY = ClassUtils.isPresent("net.sf.jsqlparser.parser.CCJSqlParser",
			ReindexerRepositoryContributor.class.getClassLoader());

	private static final Log LOG = LogFactory.getLog(ReindexerRepositoryContributor.class);

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
		constructorBuilder.addParameter("context", RepositoryFactoryBeanSupport.FragmentCreationContext.class, false);
	}

	@Override
	protected MethodContributor<? extends QueryMethod> contributeQueryMethod(Method method) {
		ReindexerQueryMethod queryMethod = new ReindexerQueryMethod(method, getRepositoryInformation(),
				getProjectionFactory());
		if ("query".equals(method.getName())) {
			return MethodContributor.forQueryMethod(queryMethod).metadataOnly(Collections::emptyMap);
		}
		if (queryMethod.hasQueryAnnotation() && USE_VISITOR_BASED_QUERY && !queryMethod.isNativeQuery()) {
			LOG.warn("""
					Using JSQLParser in AOT mode is not supported;
					Falling back to standard mode; Offending method: %s;
					Exclude JSQLParser or set `nativeQuery = true` to proceed in AOT mode.
					""".formatted(queryMethod));
			// Fallbacks to StringBasedReindexerQuery.
			return MethodContributor.forQueryMethod(queryMethod)
				.metadataOnly(() -> Map.of("query", queryMethod.getQuery()));
		}
		Map<String, Object> serialized = new HashMap<>();
		QueryMetadata queryMetadata = () -> serialized;
		return MethodContributor.forQueryMethod(queryMethod).withMetadata(queryMetadata).contribute(context -> {
			CodeBlock.Builder body = CodeBlock.builder();
			AotQuery aotQuery;
			CodeBlock executionCodeBlock;
			if (queryMethod.hasQueryAnnotation()) {
				aotQuery = ReindexerCodeBlocks.stringQueryCodeBlockBuilder(context, queryMethod).build();
				executionCodeBlock = ReindexerCodeBlocks.stringQueryExecutionCodeBlockBuilder(context, queryMethod)
					.build();
			}
			else {
				PartTree tree = new PartTree(queryMethod.getName(), queryMethod.getDomainClass());
				aotQuery = ReindexerCodeBlocks
					.derivedQueryCodeBlockBuilder(tree, context, this.mappingContext, queryMethod)
					.build();
				executionCodeBlock = ReindexerCodeBlocks
					.derivedExecutionCodeBlockBuilder(tree, context, this.mappingContext, queryMethod)
					.build();
			}
			body.add(aotQuery.codeBlockQuery());
			body.add(";\n");
			body.add(executionCodeBlock);
			serialized.put("query", aotQuery.stringQuery());
			return body.build();
		});
	}

	private static final class ReindexerAotRepositoryContextSupport extends AotRepositoryContextSupport {

		private final AotRepositoryContext delegate;

		private final Lazy<AotRepositoryInformation> repositoryInformation;

		private ReindexerAotRepositoryContextSupport(AotRepositoryContext delegate) {
			super(delegate);
			this.delegate = delegate;
			this.repositoryInformation = Lazy.of(() -> {
				RepositoryInformation information = delegate.getRepositoryInformation();
				ReindexerDefaultRepositoryMetadata metadata = new ReindexerDefaultRepositoryMetadata(
						information.getRepositoryInterface());
				return new AotRepositoryInformation(metadata, information.getRepositoryBaseClass(),
						information.getFragments());
			});
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
			return this.repositoryInformation.get();
		}

	}

}
