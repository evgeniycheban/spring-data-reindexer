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
package org.springframework.data.reindexer.core.mapping;

import ru.rt.restream.reindexer.NamespaceOptions;

import org.springframework.data.expression.ValueExpression;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.util.TypeInformation;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Reindexer specific {@link ReindexerPersistentProperty} implementation that adds Reindexer specific meta-data such as the
 * namespace name and {@link NamespaceOptions}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class BasicReindexerPersistentEntity<T> extends BasicPersistentEntity<T, ReindexerPersistentProperty>
		implements ReindexerPersistentEntity<T> {

	private static final ValueExpressionParser PARSER = ValueExpressionParser.create(SpelExpressionParser::new);

	private final String namespace;

	private final NamespaceOptions namespaceOptions;

	private final ValueExpression expression;

	/**
	 * Creates an instance.
	 *
	 * @param information the {@link TypeInformation} to use
	 */
	public BasicReindexerPersistentEntity(TypeInformation<T> information) {
		super(information);
		Class<?> rawType = information.getType();
		String fallback = getPreferredNamespaceName(rawType);
		if (isAnnotationPresent(Namespace.class)) {
			Namespace namespace = getRequiredAnnotation(Namespace.class);
			this.namespace = StringUtils.hasText(namespace.name()) ? namespace.name() : fallback;
			this.namespaceOptions = new NamespaceOptions(namespace.enableStorage(), namespace.createStorageIfMissing(),
					namespace.dropOnIndexesConflict(), namespace.dropOnFileFormatError(), namespace.disableObjCache(),
					namespace.objCacheItemsCount());
			this.expression = detectExpression(namespace.name());
		}
		else {
			this.namespace = fallback;
			this.namespaceOptions = NamespaceOptions.defaultOptions();
			this.expression = null;
		}
	}

	@Override
	public String getNamespace() {
		return this.expression != null ? ObjectUtils.nullSafeToString(this.expression.evaluate(getValueEvaluationContext(null))) : this.namespace;
	}

	@Override
	public NamespaceOptions getNamespaceOptions() {
		return this.namespaceOptions;
	}

	private static String getPreferredNamespaceName(Class<?> entityClass) {
		return StringUtils.uncapitalize(entityClass.getSimpleName());
	}

	private static ValueExpression detectExpression(String potentialExpression) {
		if (!StringUtils.hasText(potentialExpression)) {
			return null;
		}
		ValueExpression expression = PARSER.parse(potentialExpression);
		return expression.isLiteral() ? null : expression;
	}

}
