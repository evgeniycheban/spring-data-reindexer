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
package org.springframework.data.reindexer.repository.query;

import org.junit.jupiter.api.Test;

import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.reindexer.repository.item.TestItemReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.support.ReindexerDefaultRepositoryMetadata;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link ReindexerQueryMethod}.
 *
 * @author Evgeniy Cheban
 */
class ReindexerQueryMethodTests {

	@Test
	void isIteratorQueryWhenMethodReturnsResultIteratorThenTrue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorByName", String.class);
		assertThat(method.isIteratorQuery()).isTrue();
	}

	@Test
	void isIteratorQueryWhenMethodDoesNotReturnResultIteratorThenFalse() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findByNonIndexValue", String.class);
		assertThat(method.isIteratorQuery()).isFalse();
	}

	@Test
	void hasQueryAnnotationWhenMethodHasQueryAnnotationThenTrue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorSqlByName", String.class);
		assertThat(method.hasQueryAnnotation()).isTrue();
	}

	@Test
	void hasQueryAnnotationWhenMethodDoesNotHaveQueryAnnotationThenFalse() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorByName", String.class);
		assertThat(method.hasQueryAnnotation()).isFalse();
	}

	@Test
	void getQueryWhenMethodHasQueryAnnotationThenReturnsValue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorSqlByName", String.class);
		assertThat(method.getQuery()).isEqualTo("SELECT * FROM items WHERE name = ?1");
	}

	@Test
	void getQueryWhenMethodDoesNotHaveQueryAnnotationThenException() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorByName", String.class);
		assertThatIllegalStateException().isThrownBy(method::getQuery)
			.withMessage("Expected lazy evaluation to yield a non-null value but got null");
	}

	@Test
	void isModifyingQueryWhenQueryAnnotationHasUpdateTrueThenTrue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("updateNameSql", String.class, Long.class);
		assertThat(method.isModifyingQuery()).isTrue();
	}

	@Test
	void isModifyingQueryWhenQueryAnnotationHasUpdateFalseThenFalse() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorSqlByName", String.class);
		assertThat(method.isModifyingQuery()).isFalse();
	}

	@Test
	void isNativeQueryWhenQueryAnnotationHasNativeTrueThenTrue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorNativeSqlByName", String.class);
		assertThat(method.isNativeQuery()).isTrue();
	}

	@Test
	void isNativeQueryWhenQueryAnnotationHasNativeFalseThenFalse() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findIteratorSqlByName", String.class);
		assertThat(method.isNativeQuery()).isFalse();
	}

	@Test
	void hasAnnotatedCollationWhenAnnotationWithNotBlankValueThenReturnsValue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findByNonIndexValue", String.class);
		assertThat(method.hasAnnotatedCollation()).isTrue();
	}

	@Test
	void getAnnotatedCollationWhenCollationAnnotationWithNotBlankValueThenReturnsValue() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findByNonIndexValue", String.class);
		assertThat(method.getAnnotatedCollation()).isEqualTo("utf8");
	}

	@Test
	void getAnnotatedCollationWhenNoCollationAnnotationThenException() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findByName", String.class);
		assertThatIllegalStateException().isThrownBy(method::getAnnotatedCollation)
			.withMessage(
					"Expected to find @Collation annotation but did not; Make sure to check hasAnnotatedCollation() before.");
	}

	@Test
	void getDomainClassReturnsDomainClass() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findByName", String.class);
		assertThat(method.getDomainClass()).isEqualTo(TestItem.class);
	}

	@Test
	void getParametersReturnsReindexerParameters() {
		ReindexerQueryMethod method = createReindexerQueryMethod("findByName", String.class);
		ReindexerParameters parameters = method.getParameters();
		assertThat(parameters).hasSize(1);
		assertThat(parameters).map(Parameter::getRequiredName).containsExactly("name");
	}

	private ReindexerQueryMethod createReindexerQueryMethod(String methodName, Class<?>... parameterTypes) {
		ReindexerDefaultRepositoryMetadata metadata = new ReindexerDefaultRepositoryMetadata(
				TestItemReindexerRepository.class);
		SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
		return new ReindexerQueryMethod(
				ReflectionUtils.getRequiredMethod(TestItemReindexerRepository.class, methodName, parameterTypes),
				metadata, factory);
	}

}
