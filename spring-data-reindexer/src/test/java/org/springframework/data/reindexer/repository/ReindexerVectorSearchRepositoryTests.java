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

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import ru.rt.restream.reindexer.vector.params.KnnParams;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;
import org.springframework.data.reindexer.repository.item.TestItemFloatVectorRepository;
import org.springframework.data.reindexer.repository.item.dto.TestItemFloatVectorRecord;
import org.springframework.data.reindexer.repository.item.entity.TestItemFloatVector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReindexerRepository}'s vector search methods.
 *
 * @author Evgeniy Cheban
 */
class ReindexerVectorSearchRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestItemFloatVectorRepository itemFloatVectorRepository;

	@Test
	void findAllByEmbeddingHnswNear() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingHnswNear(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllRankedByEmbeddingHnswNear() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<SearchResult<TestItemFloatVector>> actual = this.itemFloatVectorRepository
			.findAllRankedByEmbeddingHnswNear(Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
					KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
	}

	@Test
	void findAllResultsByEmbeddingHnswNear() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		SearchResults<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllResultsByEmbeddingHnswNear(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
	}

	@Test
	void streamAllByEmbeddingHnswNear() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		Stream<SearchResult<TestItemFloatVector>> stream = this.itemFloatVectorRepository.streamAllByEmbeddingHnswNear(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		try (stream) {
			List<SearchResult<TestItemFloatVector>> actual = stream.toList();
			assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
			assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
		}
	}

	@Test
	void findAllByEmbeddingHnswWithin() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingHnswWithin(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllRecordByEmbeddingHnswNear() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVectorRecord> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .map(it -> new TestItemFloatVectorRecord(it.getId(), it.getEmbeddingHnsw()))
                .toList();
        // @formatter:on
		List<TestItemFloatVectorRecord> actual = this.itemFloatVectorRepository.findAllRecordByEmbeddingHnswNear(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllByEmbeddingHnswNearAndIdIn() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 3)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingHnswNearAndIdIn(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5), List.of(1L, 2L, 3L));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllByEmbeddingHnswSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingHnswSql(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllByEmbeddingFloatArrayHnswSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingFloatArrayHnswSql(
				new float[] { 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f },
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllRankedByEmbeddingHnswSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<SearchResult<TestItemFloatVector>> actual = this.itemFloatVectorRepository.findAllRankedByEmbeddingHnswSql(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
	}

	@Test
	void findAllResultsByEmbeddingHnswSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		SearchResults<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllResultsByEmbeddingHnswSql(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
	}

	@Test
	void streamAllByEmbeddingHnswSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		Stream<SearchResult<TestItemFloatVector>> stream = this.itemFloatVectorRepository.streamAllByEmbeddingHnswSql(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		try (stream) {
			List<SearchResult<TestItemFloatVector>> actual = stream.toList();
			assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
			assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
		}
	}

	@Test
	void findAllByEmbeddingHnswNativeSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingHnswNativeSql(
				Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllByEmbeddingFloatArrayHnswNativeSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<TestItemFloatVector> actual = this.itemFloatVectorRepository.findAllByEmbeddingFloatArrayHnswNativeSql(
				new float[] { 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f },
				KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void findAllRankedByEmbeddingHnswNativeSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		List<SearchResult<TestItemFloatVector>> actual = this.itemFloatVectorRepository
			.findAllRankedByEmbeddingHnswNativeSql(Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
					KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
	}

	@Test
	void findAllResultsByEmbeddingHnswNativeSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		SearchResults<TestItemFloatVector> actual = this.itemFloatVectorRepository
			.findAllResultsByEmbeddingHnswNativeSql(Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
					KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
	}

	@Test
	void streamAllByEmbeddingHnswNativeSql() {
		// @formatter:off
        List<TestItemFloatVector> entities = getTestItemFloatVectors()
                .map(this.itemFloatVectorRepository::save)
                .toList();
        List<TestItemFloatVector> expected = IntStream.rangeClosed(1, 4)
                .mapToObj(entities::get)
                .toList();
        // @formatter:on
		Stream<SearchResult<TestItemFloatVector>> stream = this.itemFloatVectorRepository
			.streamAllByEmbeddingHnswNativeSql(Vector.of(0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f, 0.23f),
					KnnParams.hnsw(KnnParams.radius(0.4f), 5));
		try (stream) {
			List<SearchResult<TestItemFloatVector>> actual = stream.toList();
			assertThat(actual).map(SearchResult::getContent).containsExactlyInAnyOrderElementsOf(expected);
			assertThat(actual).map(SearchResult::getScore).allMatch((score) -> score.getValue() < 0.4);
		}
	}

	private static Stream<TestItemFloatVector> getTestItemFloatVectors() {
		// @formatter:off
        return Stream.of(
                TestItemFloatVector.builder()
                        .id(0L)
                        .embeddingHnsw(Vector.of(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(1L)
                        .embeddingHnsw(Vector.of(0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(2L)
                        .embeddingHnsw(Vector.of(0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(3L)
                        .embeddingHnsw(Vector.of(0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(4L)
                        .embeddingHnsw(Vector.of(0.4f, 0.4f, 0.4f, 0.4f, 0.4f, 0.4f, 0.4f, 0.4f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(5L)
                        .embeddingHnsw(Vector.of(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(6L)
                        .embeddingHnsw(Vector.of(0.6f, 0.6f, 0.6f, 0.6f, 0.6f, 0.6f, 0.6f, 0.6f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(7L)
                        .embeddingHnsw(Vector.of(0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(8L)
                        .embeddingHnsw(Vector.of(0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f))
                        .build(),
                TestItemFloatVector.builder()
                        .id(9L)
                        .embeddingHnsw(Vector.of(0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f))
                        .build()
        );
        // @formatter:on
	}

}
