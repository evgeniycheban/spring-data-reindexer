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

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link ValidatingEntityCallback}.
 *
 * @author Evgeniy Cheban
 */
class ValidatingEntityCallbackTests {

	@Test
	void onBeforeSaveWhenDoesNotHaveValidationViolationsThenPasses() {
		ValidatingEntityCallback callback = createValidatingEntityCallback();
		callback.onBeforeSave(new Person("John", 21), "persons");
	}

	@Test
	void onBeforeSaveWhenHasValidationViolationsThenException() {
		ValidatingEntityCallback callback = createValidatingEntityCallback();
		assertThatExceptionOfType(ConstraintViolationException.class)
			.isThrownBy(() -> callback.onBeforeSave(new Person("   ", 20), "persons"))
			.satisfies(ex -> assertThat(ex.getConstraintViolations()).hasSize(2));
	}

	@Test
	void getOrderReturnsDefaultValue() {
		ValidatingEntityCallback callback = createValidatingEntityCallback();
		assertThat(callback.getOrder()).isEqualTo(AuditingEntityCallback.DEFAULT_ORDER);
	}

	@Test
	void setOrderSetsNewValue() {
		int order = 200;
		ValidatingEntityCallback callback = createValidatingEntityCallback();
		callback.setOrder(order);
		assertThat(callback.getOrder()).isEqualTo(order);
	}

	@Test
	void instantiateWhenValidatorNullThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ValidatingEntityCallback(null))
			.withMessage("Validator must not be null");
	}

	record Person(@NotBlank String name, @NotNull @Min(21) Integer age) {
	}

	private ValidatingEntityCallback createValidatingEntityCallback() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			return new ValidatingEntityCallback(factory.getValidator());
		}
	}

}
