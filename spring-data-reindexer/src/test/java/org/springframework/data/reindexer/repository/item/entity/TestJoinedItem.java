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

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.annotations.Transient;

import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;

/**
 * @author Evgeniy Cheban
 */
@Namespace(name = "test_joined_items")
@Data
@NoArgsConstructor
public class TestJoinedItem {

	@Reindex(name = "id", isPrimaryKey = true)
	private Long id;

	@Reindex(name = "name")
	private String name;

	@Reindex(name = "value")
	private String value;

	@Reindex(name = "price")
	private Double price;

	@Reindex(name = "nestedJoinedItemId")
	private Long nestedJoinedItemId;

	@Transient
	@NamespaceReference(indexName = "nestedJoinedItemId", joinType = JoinType.LEFT, fetch = true)
	private TestJoinedItem nestedJoinedItem;

	@Transient
	@NamespaceReference(indexName = "nestedJoinedItemId", lazy = true)
	private TestJoinedItem nestedJoinedItemLazy;

	public TestJoinedItem(Long id, String name) {
		this.id = id;
		this.name = name;
	}

	public TestJoinedItem(Long id, Long nestedJoinedItemId, String name) {
		this.id = id;
		this.nestedJoinedItemId = nestedJoinedItemId;
		this.name = name;
	}

	public TestJoinedItem(Long id, String name, String value, Double price) {
		this.id = id;
		this.name = name;
		this.value = value;
		this.price = price;
	}

	public TestJoinedItem(Long id, Double price) {
		this.id = id;
		this.price = price;
	}

}
