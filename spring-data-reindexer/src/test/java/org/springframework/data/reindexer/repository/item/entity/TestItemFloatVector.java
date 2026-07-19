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
package org.springframework.data.reindexer.repository.item.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.rt.restream.reindexer.annotations.Hnsw;
import ru.rt.restream.reindexer.annotations.Metric;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.domain.Vector;
import org.springframework.data.reindexer.core.mapping.Namespace;

/**
 * @author Evgeniy Cheban
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Namespace(name = "test_item_float_vectors")
public class TestItemFloatVector {

	@Reindex(name = "id", isPrimaryKey = true)
	private Long id;

	@Reindex(name = "embeddingHnsw")
	@Hnsw(metric = Metric.L2, dimension = 8)
	private Vector embeddingHnsw;

}
