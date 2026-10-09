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

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.util.Assert;

/**
 * An {@link org.springframework.data.mapping.callback.EntityCallback} to populate
 * auditing related fields on an entity about to be saved.
 *
 * @author Evgeniy Cheban
 * @since 1.8
 */
public class AuditingEntityCallback implements BeforeConvertCallback<Object>, Ordered {

	static final int DEFAULT_ORDER = 100;

	private final ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory;

	private int order = DEFAULT_ORDER;

	/**
	 * Creates an instance.
	 * @param auditingHandlerFactory will never be {@literal null}
	 */
	public AuditingEntityCallback(ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory) {
		Assert.notNull(auditingHandlerFactory, "IsNewAwareAuditingHandler must not be null");
		this.auditingHandlerFactory = auditingHandlerFactory;
	}

	@Override
	public Object onBeforeConvert(Object entity, String namespace) {
		return this.auditingHandlerFactory.getObject().markAudited(entity);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Specify the order value for this {@link BeforeConvertCallback}. Defaults to
	 * {@code 100}.
	 * @param order the order value to use
	 * @see Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

}
