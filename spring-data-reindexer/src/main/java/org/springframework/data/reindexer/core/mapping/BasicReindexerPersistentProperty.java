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

	private final Lazy<Set<String>> lookupVariables;

	/**
	 * Creates a new {@link BasicReindexerPersistentProperty}.
	 * @param property must not be {@literal null}
	 * @param owner must not be {@literal null}
	 * @param simpleTypeHolder must not be {@literal null}
	 */
	public BasicReindexerPersistentProperty(Property property, PersistentEntity<?, ReindexerPersistentProperty> owner,
			SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);
		this.lookupVariables = Lazy.of(() -> {
			if (isNamespaceReference()) {
				NamespaceReference namespaceReference = getNamespaceReference();
				if (StringUtils.hasText(namespaceReference.lookup())) {
					Set<String> variables = new HashSet<>();
					Expression expression = PARSER.parseExpression(namespaceReference.lookup(),
							ParserContext.TEMPLATE_EXPRESSION);
					traverseAndPopulateVariables(expression, variables);
					return Set.copyOf(variables);
				}
			}
			return Set.of();
		});
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

	private void traverseAndPopulateVariables(Expression expression, Set<String> variables) {
		if (expression instanceof CompositeStringExpression compositeStringExpression) {
			for (Expression expr : compositeStringExpression.getExpressions()) {
				traverseAndPopulateVariables(expr, variables);
			}
		}
		else if (expression instanceof SpelExpression spelExpression) {
			traverseAndPopulateVariables(spelExpression.getAST(), variables);
		}
	}

	private void traverseAndPopulateVariables(SpelNode node, Set<String> variables) {
		if (node instanceof PropertyOrFieldReference reference) {
			variables.add(reference.toStringAST());
		}
		else if (node instanceof VariableReference reference) {
			variables.add(reference.toStringAST());
		}
		else {
			for (int i = 0; i < node.getChildCount(); i++) {
				traverseAndPopulateVariables(node.getChild(i), variables);
			}
		}
	}

}
