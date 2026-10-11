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
package org.springframework.data.reindexer.repository;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.reindexer.repository.item.TestValidatingItemRepository;
import org.springframework.data.reindexer.repository.item.entity.TestValidatingItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link ReindexerRepository}'s JSR-303 validation support.
 *
 * @author Evgeniy Cheban
 */
class ReindexerValidatingRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestValidatingItemRepository repository;

	@Test
	void saveWhenDoesNotHaveValidationViolationsThenPasses() {
		TestValidatingItem saved = this.repository.save(new TestValidatingItem(1L, "TestName", 0));
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo(1L);
		assertThat(saved.getName()).isEqualTo("TestName");
		assertThat(saved.getValue()).isEqualTo(0);
	}

	@Test
	void saveTriggersValidationAfterBeforeConvertCallback() {
		TestValidatingItem saved = this.repository.save(new TestValidatingItem(1L, "TestName", null));
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo(1L);
		assertThat(saved.getName()).isEqualTo("TestName");
		assertThat(saved.getValue()).isEqualTo(0); // Set in beforeValidityCallback
	}

	@Test
	void saveWhenHasValidationViolationThenException() {
		assertThatExceptionOfType(ConstraintViolationException.class)
			.isThrownBy(() -> this.repository.save(new TestValidatingItem(null, "   ", -1)))
			.satisfies(ex -> assertThat(ex.getConstraintViolations()).hasSize(3));
	}

}
