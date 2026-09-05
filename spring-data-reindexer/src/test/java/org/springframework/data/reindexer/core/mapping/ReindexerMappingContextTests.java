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

import java.util.Set;
import java.util.function.Supplier;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mapping.MappingException;
import ru.rt.restream.reindexer.binding.Consts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ReindexerMappingContext}.
 *
 * @author Evgeniy Cheban
 */
class ReindexerMappingContextTests {

	final ReindexerMappingContext context = new ReindexerMappingContext();

	@Test
	void isAutoIndexCreationWhenNotSetThenDefaultsToFalse() {
		assertThat(this.context.isAutoIndexCreation()).isFalse();
	}

	@Test
	void isAutoIndexCreationWhenSetThenUsesValue() {
		this.context.setAutoIndexCreation(true);
		assertThat(this.context.isAutoIndexCreation()).isTrue();
	}

	@Test
	void getQueryFormatVersionWhenNotSetThenDefaultsToV1() {
		assertThat(this.context.getQueryFormatVersion()).isEqualTo(Consts.QUERY_FORMAT_V1);
	}

	@Test
	void getQueryFormatVersionWhenSetThenSupplierAlwaysCalled() {
		Supplier<Integer> queryFormatVersion = mock(Supplier.class);
		when(queryFormatVersion.get()).thenReturn(Consts.QUERY_FORMAT_V2);
		this.context.setQueryFormatVersion(queryFormatVersion);
		assertThat(this.context.getQueryFormatVersion()).isEqualTo(Consts.QUERY_FORMAT_V2);
		assertThat(this.context.getQueryFormatVersion()).isEqualTo(Consts.QUERY_FORMAT_V2);
		// The query format version is not cached and always fetched from the supplier.
		verify(queryFormatVersion, times(2)).get();
	}

	@Test
	void getRequiredPersistentEntityWhenPresentThenReturnsEntity() {
		this.context.setInitialEntitySet(Set.of(Person.class));
		this.context.afterPropertiesSet();
		ReindexerPersistentEntity<?> entity = this.context.getRequiredPersistentEntity("persons");
		assertThat(entity).isNotNull();
		assertThat(entity.getType()).isEqualTo(Person.class);
	}

	@Test
	void getRequiredPersistentEntityWhenNotPresentThenException() {
		assertThatExceptionOfType(MappingException.class)
			.isThrownBy(() -> this.context.getRequiredPersistentEntity("non_existing_namespace"))
			.withMessageContaining("Unknown persistent entity: non_existing_namespace");
	}

	@Data
	@Namespace(name = "persons")
	static class Person {

		@Id
		Long id;

	}

}
