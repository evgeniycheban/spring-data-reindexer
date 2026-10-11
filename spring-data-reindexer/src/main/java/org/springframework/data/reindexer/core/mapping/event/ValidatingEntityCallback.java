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
package org.springframework.data.reindexer.core.mapping.event;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * JSR-303 dependant entities validator.
 * <p>
 * When registered as a Spring bean, automatically invoked after a domain object
 * conversion and before an entity is saved.
 *
 * @author Evgeniy Cheban
 * @since 1.8
 */
public class ValidatingEntityCallback implements BeforeSaveCallback<Object>, Ordered {

	private static final Log logger = LogFactory.getLog(ValidatingEntityCallback.class);

	static final int DEFAULT_ORDER = 100;

	private final Validator validator;

	private int order = DEFAULT_ORDER;

	public ValidatingEntityCallback(Validator validator) {
		Assert.notNull(validator, "Validator must not be null");
		this.validator = validator;
	}

	@Override
	public Object onBeforeSave(Object entity, String namespace) {
		if (logger.isDebugEnabled()) {
			logger.debug("Validating domain object: %s for namespace: %s".formatted(entity, namespace));
		}
		Set<ConstraintViolation<Object>> violations = this.validator.validate(entity);
		if (!violations.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("During domain object: %s for namespace: %s validation violations found: %s"
					.formatted(entity, namespace, violations));
			}
			throw new ConstraintViolationException(violations);
		}
		return entity;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Specify the order value for this {@link BeforeSaveCallback}. Defaults to
	 * {@code 100}.
	 * @param order the order value to use
	 * @see Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

}
