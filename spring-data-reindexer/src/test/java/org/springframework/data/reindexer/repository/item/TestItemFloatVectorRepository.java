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
package org.springframework.data.reindexer.repository.item;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.item.dto.TestItemFloatVectorRecord;
import org.springframework.data.reindexer.repository.item.entity.TestItemFloatVector;
import org.springframework.stereotype.Repository;
import ru.rt.restream.reindexer.vector.params.KnnSearchParam;

/**
 * @author Evgeniy Cheban
 */
@Repository
public interface TestItemFloatVectorRepository extends ReindexerRepository<TestItemFloatVector, Long> {

	List<TestItemFloatVector> findAllByEmbeddingHnswNear(Vector vector, KnnSearchParam knnSearchParam);

	List<SearchResult<TestItemFloatVector>> findAllRankedByEmbeddingHnswNear(Vector vector,
			KnnSearchParam knnSearchParam);

	SearchResults<TestItemFloatVector> findAllResultsByEmbeddingHnswNear(Vector vector, KnnSearchParam knnSearchParam);

	Stream<SearchResult<TestItemFloatVector>> streamAllByEmbeddingHnswNear(Vector vector,
			KnnSearchParam knnSearchParam);

	List<TestItemFloatVector> findAllByEmbeddingHnswWithin(Vector vector, KnnSearchParam knnSearchParam);

	List<TestItemFloatVectorRecord> findAllRecordByEmbeddingHnswNear(Vector vector, KnnSearchParam knnSearchParam);

	List<TestItemFloatVector> findAllByEmbeddingHnswNearAndIdIn(Vector vector, KnnSearchParam knnSearchParam,
			List<Long> ids);

	@Query("select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)")
	List<TestItemFloatVector> findAllByEmbeddingHnswSql(Vector vector, KnnSearchParam knnSearchParam);

	@Query("select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)")
	List<TestItemFloatVector> findAllByEmbeddingFloatArrayHnswSql(float[] vector, KnnSearchParam knnSearchParam);

	@Query("select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)")
	List<SearchResult<TestItemFloatVector>> findAllRankedByEmbeddingHnswSql(Vector vector,
			KnnSearchParam knnSearchParam);

	@Query("select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)")
	SearchResults<TestItemFloatVector> findAllResultsByEmbeddingHnswSql(Vector vector, KnnSearchParam knnSearchParam);

	@Query("select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)")
	Stream<SearchResult<TestItemFloatVector>> streamAllByEmbeddingHnswSql(Vector vector, KnnSearchParam knnSearchParam);

	@Query(value = "select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)",
			nativeQuery = true)
	List<TestItemFloatVector> findAllByEmbeddingHnswNativeSql(Vector vector, KnnSearchParam knnSearchParam);

	@Query(value = "select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)",
			nativeQuery = true)
	List<TestItemFloatVector> findAllByEmbeddingFloatArrayHnswNativeSql(float[] vector, KnnSearchParam knnSearchParam);

	@Query(value = "select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)",
			nativeQuery = true)
	List<SearchResult<TestItemFloatVector>> findAllRankedByEmbeddingHnswNativeSql(Vector vector,
			KnnSearchParam knnSearchParam);

	@Query(value = "select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)",
			nativeQuery = true)
	SearchResults<TestItemFloatVector> findAllResultsByEmbeddingHnswNativeSql(Vector vector,
			KnnSearchParam knnSearchParam);

	@Query(value = "select *, vectors(), rank() from test_item_float_vectors where knn(embeddingHnsw, :vector, :knnSearchParam)",
			nativeQuery = true)
	Stream<SearchResult<TestItemFloatVector>> streamAllByEmbeddingHnswNativeSql(Vector vector,
			KnnSearchParam knnSearchParam);

}
