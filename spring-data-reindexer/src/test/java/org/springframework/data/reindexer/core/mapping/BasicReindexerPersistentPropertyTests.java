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

import org.junit.jupiter.api.Test;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.annotation.Id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link ReindexerPersistentProperty}.
 *
 * @author Evgeniy Cheban
 */
class BasicReindexerPersistentPropertyTests {

	final ReindexerMappingContext context = new ReindexerMappingContext();

	final ReindexerPersistentEntity<?> entity = this.context.getRequiredPersistentEntity(Person.class);

	@Test
	void getReindexWhenHasReindexAnnotationThenReturnsReindexAnnotation() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("firstName");
		assertThat(property.getReindex().name()).isEqualTo("first_name");
	}

	@Test
	void getReindexWhenDoesNotHaveReindexAnnotationThenException() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("lastName");
		assertThatIllegalStateException().isThrownBy(property::getReindex);
	}

	@Test
	void isIndexedPropertyWhenHasReindexAnnotationThenReturnsTrue() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("firstName");
		assertThat(property.isIndexedProperty()).isTrue();
	}

	@Test
	void isIndexedPropertyWhenDoesNotHaveReindexAnnotationThenReturnsFalse() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("lastName");
		assertThat(property.isIndexedProperty()).isFalse();
	}

	@Test
	void getIndexNameWhenHasReindexAnnotationThenReturnsReindexAnnotationName() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("firstName");
		assertThat(property.getIndexName()).isEqualTo("first_name");
	}

	@Test
	void getIndexNameWhenDoesNotHaveReindexAnnotationThenReturnsPropertyName() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("lastName");
		assertThat(property.getIndexName()).isEqualTo("lastName");
	}

	static class Person {

		@Id
		Long id;

		@Reindex(name = "first_name")
		String firstName;

		String lastName;

	}

}
