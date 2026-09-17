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
package org.springframework.data.reindexer.core.mapping;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.annotations.Transient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.Lazy;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.CompositeStringExpression;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.VariableReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

/**
 * Reindexer specific {@link org.springframework.data.mapping.PersistentProperty}
 * implementation.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public class BasicReindexerPersistentProperty extends AnnotationBasedPersistentProperty<ReindexerPersistentProperty>
		implements ReindexerPersistentProperty {

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private final Lazy<Boolean> isIdProperty = Lazy.of(() -> {
		if (super.isIdProperty()) {
			return true;
		}
		Reindex reindex = findAnnotation(Reindex.class);
		return reindex != null && reindex.isPrimaryKey();
	});

	private final Lazy<Boolean> isTransient = Lazy.of(() -> !isNamespaceReference() && !isAnnotationPresent(Value.class)
			&& (super.isTransient() || isAnnotationPresent(Transient.class)));

	private final Supplier<Set<String>> lookupVariables;

	/**
	 * Creates a new {@link BasicReindexerPersistentProperty}.
	 * @param property must not be {@literal null}
	 * @param owner must not be {@literal null}
	 * @param simpleTypeHolder must not be {@literal null}
	 */
	public BasicReindexerPersistentProperty(Property property, PersistentEntity<?, ReindexerPersistentProperty> owner,
			SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);
		if (isNamespaceReference()) {
			NamespaceReference namespaceReference = getNamespaceReference();
			this.lookupVariables = StringUtils.hasText(namespaceReference.lookup())
					? new ExpressionVariablesExtractor(namespaceReference.lookup()) : Set::of;
		}
		else {
			this.lookupVariables = Set::of;
		}
	}

	@Override
	protected Association<ReindexerPersistentProperty> createAssociation() {
		return new Association<>(this, null);
	}

	@Override
	public boolean isNamespaceReference() {
		return findAnnotation(NamespaceReference.class) != null;
	}

	@Override
	public boolean isIndexedProperty() {
		return findAnnotation(Reindex.class) != null;
	}

	@Override
	public NamespaceReference getNamespaceReference() {
		return getRequiredAnnotation(NamespaceReference.class);
	}

	@Override
	public Reindex getReindex() {
		return getRequiredAnnotation(Reindex.class);
	}

	@Override
	public boolean isIdProperty() {
		return this.isIdProperty.get();
	}

	@Override
	public boolean isTransient() {
		return this.isTransient.get();
	}

	@Override
	public Set<String> getLookupVariables() {
		return this.lookupVariables.get();
	}

	private static final class ExpressionVariablesExtractor implements Supplier<Set<String>> {

		private static final Log logger = LogFactory.getLog(ExpressionVariablesExtractor.class);

		private final String expression;

		private volatile @Nullable Set<String> lookupVariables;

		private ExpressionVariablesExtractor(String expression) {
			this.expression = expression;
		}

		@Override
		public Set<String> get() {
			Set<String> lookupVariables = this.lookupVariables;
			if (lookupVariables != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Accessing already resolved variables: %s from lookup expression: %s"
						.formatted(lookupVariables, this.expression));
				}
				return lookupVariables;
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Resolving variables from lookup expression: %s".formatted(this.expression));
			}
			synchronized (this) {
				lookupVariables = this.lookupVariables;
				if (lookupVariables == null) {
					Set<String> variables = new HashSet<>();
					Expression expression = PARSER.parseExpression(this.expression, ParserContext.TEMPLATE_EXPRESSION);
					traverseAndPopulateLookupVariables(expression, variables);
					lookupVariables = Set.copyOf(variables);
					this.lookupVariables = lookupVariables;
				}
				return lookupVariables;
			}
		}

		private void traverseAndPopulateLookupVariables(Expression expression, Set<String> variables) {
			if (expression instanceof CompositeStringExpression compositeStringExpression) {
				for (Expression expr : compositeStringExpression.getExpressions()) {
					traverseAndPopulateLookupVariables(expr, variables);
				}
			}
			else if (expression instanceof SpelExpression spelExpression) {
				traverseAndPopulateLookupVariables(spelExpression.getAST(), variables);
			}
		}

		private void traverseAndPopulateLookupVariables(SpelNode node, Set<String> variables) {
			if (node instanceof PropertyOrFieldReference reference) {
				variables.add(reference.toStringAST());
			}
			else if (node instanceof VariableReference reference) {
				variables.add(reference.toStringAST());
			}
			else {
				int childCount = node.getChildCount();
				for (int i = 0; i < childCount; i++) {
					traverseAndPopulateLookupVariables(node.getChild(i), variables);
				}
			}
		}

	}

}
