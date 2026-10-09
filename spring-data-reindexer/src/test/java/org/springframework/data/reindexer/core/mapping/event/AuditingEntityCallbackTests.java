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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.data.auditing.IsNewAwareAuditingHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AuditingEntityCallback}.
 *
 * @author Evgeniy Cheban
 */
class AuditingEntityCallbackTests {

	@Test
	void onBeforeConvertUsesProvidedAuditingHandler() {
		Object entity = new Object();
		IsNewAwareAuditingHandler auditingHandler = Mockito.mock(IsNewAwareAuditingHandler.class);
		when(auditingHandler.markAudited(entity)).thenReturn(entity);
		AuditingEntityCallback callback = new AuditingEntityCallback(() -> auditingHandler);
		Object result = callback.onBeforeConvert(entity, "items");
		assertThat(result).isSameAs(entity);
		verify(auditingHandler).markAudited(entity);
	}

	@Test
	void getOrderReturnsDefaultValue() {
		IsNewAwareAuditingHandler auditingHandler = Mockito.mock(IsNewAwareAuditingHandler.class);
		AuditingEntityCallback callback = new AuditingEntityCallback(() -> auditingHandler);
		assertThat(callback.getOrder()).isEqualTo(AuditingEntityCallback.DEFAULT_ORDER);
	}

	@Test
	void setOrderSetsNewValue() {
		int order = 200;
		IsNewAwareAuditingHandler auditingHandler = Mockito.mock(IsNewAwareAuditingHandler.class);
		AuditingEntityCallback callback = new AuditingEntityCallback(() -> auditingHandler);
		callback.setOrder(order);
		assertThat(callback.getOrder()).isEqualTo(order);
	}

	@Test
	void instantiateWhenAuditingHandlerNullThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AuditingEntityCallback(null))
			.withMessage("IsNewAwareAuditingHandler must not be null");
	}

}
