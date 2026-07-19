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

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.annotations.Transient;

import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;

/**
 * @author Evgeniy Cheban
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Namespace(name = "test_item_container")
public class TestItemContainer {

	@Reindex(name = "id", isPrimaryKey = true)
	private Long id;

	@Reindex(name = "mandatoryItemId")
	private Long mandatoryItemId;

	@Reindex(name = "ambiguousItemName")
	private String ambiguousItemName;

	@Reindex(name = "joinedItemNames")
	private List<String> joinedItemNames = new ArrayList<>();

	@Transient
	@NamespaceReference(indexName = "mandatoryItemId", lazy = true, nullable = false)
	private TestItem mandatoryItem;

	@Transient
	@NamespaceReference(lookup = "select * from items where id = #{mandatoryItemId}", nullable = false)
	private TestItem mandatoryItemLookup;

	@Transient
	@NamespaceReference(indexName = "ambiguousItemName", referencedIndexName = "name", lazy = true)
	private TestItem ambiguousItem;

	@Transient
	@NamespaceReference(lookup = "select * from items where name = '#{ambiguousItemName}'")
	private TestItem ambiguousItemLookup;

	@Transient
	@NamespaceReference(indexName = "joinedItemNames", referencedIndexName = "name", joinType = JoinType.LEFT)
	private List<TestItem> joinedItemsByName;

}
