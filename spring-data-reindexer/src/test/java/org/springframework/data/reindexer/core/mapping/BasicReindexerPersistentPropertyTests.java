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

import java.util.List;

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

	@Test
	void getLookupVariablesWhenHasNamespaceReferenceValueThenResolves() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("friends");
		assertThat(property.getLookupVariables()).containsExactlyInAnyOrder("friendIds", "#sortString");
	}

	@Test
	void getLookupVariablesWhenDoesNotHaveNamespaceReferenceAnnotationThenEmpty() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("friendIds");
		assertThat(property.getLookupVariables()).isEmpty();
	}

	@Test
	void getLookupVariablesWhenNamespaceReferenceValueEmptyThenEmpty() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("manager");
		assertThat(property.getLookupVariables()).isEmpty();
	}

	@Test
	void getLookupVariablesWhenNamespaceReferenceValueDoesNotHaveVariablesThenEmpty() {
		ReindexerPersistentProperty property = this.entity.getRequiredPersistentProperty("allPersons");
		assertThat(property.getLookupVariables()).isEmpty();
	}

	static class Person {

		@Id
		Long id;

		@Reindex(name = "manager_id")
		Long managerId;

		@Reindex(name = "friend_ids")
		List<Long> friendIds;

		@Reindex(name = "first_name")
		String firstName;

		String lastName;

		@NamespaceReference(lookup = "select * from persons where id in (#{friendIds}) order by #{#sortString}")
		List<Person> friends;

		@NamespaceReference(indexName = "manager_id")
		Person manager;

		@NamespaceReference(lookup = "select * from persons")
		Person allPersons;

	}

}
