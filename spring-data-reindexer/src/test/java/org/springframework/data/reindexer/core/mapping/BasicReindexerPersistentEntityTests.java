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
import ru.rt.restream.reindexer.NamespaceOptions;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.core.TypeInformation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BasicReindexerPersistentEntity}.
 *
 * @author Evgeniy Cheban
 */
class BasicReindexerPersistentEntityTests {

	@Test
	void getNamespaceWhenNamespaceAnnotationPresentThenReturnsName() {
		BasicReindexerPersistentEntity<Person> entity = new BasicReindexerPersistentEntity<>(
				TypeInformation.of(Person.class));
		assertThat(entity.getNamespace()).isEqualTo("persons");
	}

	@Test
	void getNamespaceWhenNamespaceAnnotationNotPresentThenReturnsUncapitalizedSimpleClassName() {
		BasicReindexerPersistentEntity<PersonalAccount> entity = new BasicReindexerPersistentEntity<>(
				TypeInformation.of(PersonalAccount.class));
		assertThat(entity.getNamespace()).isEqualTo("personalAccount");
	}

	@Test
	void getNamespaceWhenNamespaceAnnotationHasSpelExpressionThenEvaluatesExpression() {
		BasicReindexerPersistentEntity<Address> entity = new BasicReindexerPersistentEntity<>(
				TypeInformation.of(Address.class));
		assertThat(entity.getNamespace()).isEqualTo("Address");
	}

	@Test
	void getNamespaceOptionsWhenNamespaceAnnotationPresentThenReturnsOverriddenOptions() {
		BasicReindexerPersistentEntity<Person> entity = new BasicReindexerPersistentEntity<>(
				TypeInformation.of(Person.class));
		NamespaceOptions namespaceOptions = entity.getNamespaceOptions();
		assertThat(namespaceOptions.isEnableStorage()).isFalse();
		assertThat(namespaceOptions.isCreateStorageIfMissing()).isFalse();
		assertThat(namespaceOptions.isDropOnIndexesConflict()).isTrue();
		assertThat(namespaceOptions.isDropOnFileFormatError()).isTrue();
		assertThat(namespaceOptions.isDisableObjCache()).isTrue();
		assertThat(namespaceOptions.getObjCacheItemsCount()).isEqualTo(512000L);
	}

	@Test
	void getNamespaceOptionsWhenNamespaceAnnotationNotPresentThenReturnsDefaultOptions() {
		BasicReindexerPersistentEntity<PersonalAccount> entity = new BasicReindexerPersistentEntity<>(
				TypeInformation.of(PersonalAccount.class));
		NamespaceOptions namespaceOptions = entity.getNamespaceOptions();
		assertThat(namespaceOptions.isEnableStorage()).isEqualTo(NamespaceOptions.DEFAULT_ENABLE_STORAGE);
		assertThat(namespaceOptions.isCreateStorageIfMissing()).isEqualTo(NamespaceOptions.DEFAULT_CREATE_IF_MISSING);
		assertThat(namespaceOptions.isDropOnIndexesConflict())
			.isEqualTo(NamespaceOptions.DEFAULT_DROP_ON_INDEX_CONFLICT);
		assertThat(namespaceOptions.isDropOnFileFormatError())
			.isEqualTo(NamespaceOptions.DEFAULT_DROP_ON_FILE_FORMAT_ERROR);
		assertThat(namespaceOptions.isDisableObjCache()).isEqualTo(NamespaceOptions.DEFAULT_DISABLE_OBJ_CACHE);
		assertThat(namespaceOptions.getObjCacheItemsCount()).isEqualTo(NamespaceOptions.DEFAULT_OBJ_CACHE_ITEMS_COUNT);
	}

	@Test
	void getPersistentPropertyWhenReindexAnnotationPresentThenFindsByIndexName() {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		ReindexerPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Person.class);
		ReindexerPersistentProperty property = entity.getPersistentProperty("first_name");
		assertThat(property).isNotNull();
		assertThat(property.getName()).isEqualTo("firstName");
	}

	@Test
	void getPersistentPropertyWhenReindexAnnotationPresentThenFindsByName() {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		ReindexerPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Person.class);
		ReindexerPersistentProperty property = entity.getPersistentProperty("firstName");
		assertThat(property).isNotNull();
		assertThat(property.getName()).isEqualTo("firstName");
	}

	@Test
	void getPersistentPropertyWhenReindexAnnotationNotPresentThenFindsByName() {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		ReindexerPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Person.class);
		ReindexerPersistentProperty property = entity.getPersistentProperty("lastName");
		assertThat(property).isNotNull();
		assertThat(property.getName()).isEqualTo("lastName");
	}

	@Test
	void getPersistentPropertyWhenTransientAndReindexAnnotationsPresentThenReturnsNull() {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		ReindexerPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Person.class);
		ReindexerPersistentProperty property = entity.getPersistentProperty("middle_name");
		assertThat(property).isNull();
	}

	@Namespace(name = "persons", enableStorage = false, createStorageIfMissing = false, dropOnIndexesConflict = true,
			dropOnFileFormatError = true, disableObjCache = true, objCacheItemsCount = 512000L)
	static class Person {

		@Id
		Long id;

		@Reindex(name = "first_name")
		String firstName;

		String lastName;

		@Transient
		@Reindex(name = "middle_name")
		String middleName;

	}

	static class PersonalAccount {

	}

	@Namespace(
			name = "#{T(org.springframework.data.reindexer.core.mapping.BasicReindexerPersistentEntityTests.Address).getSimpleName()}")
	static class Address {

	}

}
