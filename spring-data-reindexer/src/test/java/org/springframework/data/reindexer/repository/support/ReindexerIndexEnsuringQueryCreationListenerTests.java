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
package org.springframework.data.reindexer.repository.support;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rt.restream.reindexer.CollateMode;
import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.IndexType;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerIndex;

import org.springframework.data.domain.Limit;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.reindexer.core.convert.ReindexerCustomConversions;
import org.springframework.data.reindexer.core.convert.MappingReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.query.PartTreeReindexerQuery;
import org.springframework.data.reindexer.repository.query.QueryParameterMapper;
import org.springframework.data.reindexer.repository.query.ReindexerQueryMethod;
import org.springframework.data.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ReindexerIndexEnsuringQueryCreationListener}.
 *
 * @author Evgeniy Cheban
 */
@ExtendWith(MockitoExtension.class)
class ReindexerIndexEnsuringQueryCreationListenerTests {

	@Spy
	final ReindexerMappingContext mappingContext = createMappingContext();

	@Mock
	ReindexerNamespaceFactory namespaceFactory;

	@Mock
	Reindexer reindexer;

	@InjectMocks
	ReindexerIndexEnsuringQueryCreationListener listener;

	@Captor
	ArgumentCaptor<ReindexerIndex> indexCaptor;

	static ReindexerMappingContext createMappingContext() {
		ReindexerCustomConversions conversions = new ReindexerCustomConversions();
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		return mappingContext;
	}

	@Test
	void onCreationWhenNestedIndexThenCreates() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByNonIndexNestedItem_Name", String.class);
		this.listener.onCreation(partTreeQuery);
		verify(this.reindexer).addIndex(eq("items"), this.indexCaptor.capture());
		ReindexerIndex index = this.indexCaptor.getValue();
		assertThat(index).isNotNull();
		assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
		assertThat(index.getName()).isEqualTo("nonIndexNestedItem.name");
		assertThat(index.getJsonPaths()).containsExactly("nonIndexNestedItem.name");
		assertThat(index.isArray()).isFalse();
		assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
		assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
		assertThat(index.getSortOrder()).isEqualTo("");
	}

	@Test
	void onCreationWhenRegularAndNestedIndexesThenCreates() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByNonIndexValueAndNonIndexNestedItem_Name",
				String.class, String.class);
		this.listener.onCreation(partTreeQuery);
		verify(this.reindexer, times(2)).addIndex(eq("items"), this.indexCaptor.capture());
		List<ReindexerIndex> createdIndexes = this.indexCaptor.getAllValues();
		assertThat(createdIndexes).hasSize(2);
		assertThat(createdIndexes).element(0).satisfies(index -> {
			assertThat(index).isNotNull();
			assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
			assertThat(index.getName()).isEqualTo("nonIndexValue");
			assertThat(index.getJsonPaths()).containsExactly("nonIndexValue");
			assertThat(index.isArray()).isFalse();
			assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
			assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
			assertThat(index.getSortOrder()).isEqualTo("");
		});
		assertThat(createdIndexes).element(1).satisfies(index -> {
			assertThat(index).isNotNull();
			assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
			assertThat(index.getName()).isEqualTo("nonIndexNestedItem.name");
			assertThat(index.getJsonPaths()).containsExactly("nonIndexNestedItem.name");
			assertThat(index.isArray()).isFalse();
			assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
			assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
			assertThat(index.getSortOrder()).isEqualTo("");
		});
	}

	@Test
	void onCreationWhenMultipleNestedIndexesThenCreates() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByNonIndexNestedItem_NameAndPlace_Country",
				String.class, String.class);
		this.listener.onCreation(partTreeQuery);
		verify(this.reindexer, times(2)).addIndex(eq("items"), this.indexCaptor.capture());
		List<ReindexerIndex> createdIndexes = this.indexCaptor.getAllValues();
		assertThat(createdIndexes).hasSize(2);
		assertThat(createdIndexes).element(0).satisfies(index -> {
			assertThat(index).isNotNull();
			assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
			assertThat(index.getName()).isEqualTo("nonIndexNestedItem.name");
			assertThat(index.getJsonPaths()).containsExactly("nonIndexNestedItem.name");
			assertThat(index.isArray()).isFalse();
			assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
			assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
			assertThat(index.getSortOrder()).isEqualTo("");
		});
		assertThat(createdIndexes).element(1).satisfies(index -> {
			assertThat(index).isNotNull();
			assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
			assertThat(index.getName()).isEqualTo("place.country");
			assertThat(index.getJsonPaths()).containsExactly("place.country");
			assertThat(index.isArray()).isFalse();
			assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
			assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
			assertThat(index.getSortOrder()).isEqualTo("");
		});
	}

	@Test
	void onCreationWhenNestedAndNestedArrayIndexesThenCreates() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByPlace_CountryAndPlace_CitiesContaining",
				String.class, String.class);
		this.listener.onCreation(partTreeQuery);
		verify(this.reindexer, times(2)).addIndex(eq("items"), this.indexCaptor.capture());
		List<ReindexerIndex> createdIndexes = this.indexCaptor.getAllValues();
		assertThat(createdIndexes).hasSize(2);
		assertThat(createdIndexes).element(0).satisfies(index -> {
			assertThat(index).isNotNull();
			assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
			assertThat(index.getName()).isEqualTo("place.country");
			assertThat(index.getJsonPaths()).containsExactly("place.country");
			assertThat(index.isArray()).isFalse();
			assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
			assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
			assertThat(index.getSortOrder()).isEqualTo("");
		});
		assertThat(createdIndexes).element(1).satisfies(index -> {
			assertThat(index).isNotNull();
			assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
			assertThat(index.getName()).isEqualTo("place.cities");
			assertThat(index.getJsonPaths()).containsExactly("place.cities");
			assertThat(index.isArray()).isTrue();
			assertThat(index.getCollateMode()).isEqualTo(CollateMode.NONE);
			assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
			assertThat(index.getSortOrder()).isEqualTo("");
		});
	}

	@Test
	void onCreateWhenCollationOnMethodThenCreates() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByNonIndexValue", String.class);
		this.listener.onCreation(partTreeQuery);
		verify(this.reindexer).addIndex(eq("items"), this.indexCaptor.capture());
		ReindexerIndex index = this.indexCaptor.getValue();
		assertThat(index).isNotNull();
		assertThat(index.getFieldType()).isEqualTo(FieldType.STRING);
		assertThat(index.getName()).isEqualTo("nonIndexValue");
		assertThat(index.getJsonPaths()).containsExactly("nonIndexValue");
		assertThat(index.isArray()).isFalse();
		assertThat(index.getCollateMode()).isEqualTo(CollateMode.UTF8);
		assertThat(index.getIndexType()).isEqualTo(IndexType.DEFAULT);
		assertThat(index.getSortOrder()).isEqualTo("");
	}

	@Test
	void onCreationWhenNoPredicateThenSkips() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findAllBy", Limit.class);
		this.listener.onCreation(partTreeQuery);
		verifyNoIndexCreated();
	}

	@Test
	void onCreationWhenUnmappedTypeThenSkips() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByDefaultDateTime", LocalDateTime.class);
		this.listener.onCreation(partTreeQuery);
		verifyNoIndexCreated();
	}

	@Test
	void onCreationWhenIndexedPropertyThenSkips() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByName", String.class);
		this.listener.onCreation(partTreeQuery);
		verifyNoIndexCreated();
	}

	@Test
	void onCreationWhenPrimaryKeyPropertyThenSkips() {
		PartTreeReindexerQuery partTreeQuery = createPartTreeQuery("findByIdIn", List.class);
		this.listener.onCreation(partTreeQuery);
		verifyNoIndexCreated();
	}

	private PartTreeReindexerQuery createPartTreeQuery(String methodName, Class<?>... parameterTypes) {
		ReindexerDefaultRepositoryMetadata metadata = new ReindexerDefaultRepositoryMetadata(
				TestItemReindexerRepository.class);
		SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
		ReindexerQueryMethod method = new ReindexerQueryMethod(
				ReflectionUtils.getRequiredMethod(TestItemReindexerRepository.class, methodName, parameterTypes),
				metadata, factory);
		ReindexerPersistentEntity<?> entity = this.mappingContext.getRequiredPersistentEntity(metadata.getDomainType());
		MappingReindexerEntityInformation<?, Object> entityInformation = new MappingReindexerEntityInformation<>(
				entity);
		MappingReindexerConverter converter = new MappingReindexerConverter(this.reindexer, this.mappingContext,
				this.namespaceFactory);
		QueryParameterMapper queryParameterMapper = new QueryParameterMapper(TestItem.class, this.mappingContext,
				converter);
		return new PartTreeReindexerQuery(method, entityInformation, this.mappingContext, this.namespaceFactory,
				queryParameterMapper, converter);
	}

	private void verifyNoIndexCreated() {
		verify(this.reindexer, times(0)).addIndex(anyString(), any(ReindexerIndex.class));
	}

}
