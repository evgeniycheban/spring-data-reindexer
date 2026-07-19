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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ru.rt.restream.reindexer.EnumType;
import ru.rt.restream.reindexer.annotations.Enumerated;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.annotations.Transient;

import org.springframework.data.annotation.Id;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.reindexer.core.mapping.JoinType;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.repository.item.converter.LocalDatePropertyValueConverter;
import org.springframework.data.reindexer.repository.item.converter.LocalDateTimePropertyValueConverter;
import org.springframework.data.reindexer.repository.item.converter.LocalTimePropertyValueConverter;
import org.springframework.data.reindexer.repository.item.dto.Place;
import org.springframework.data.reindexer.repository.item.dto.Price;
import org.springframework.data.reindexer.repository.item.dto.TestEnum;
import org.springframework.data.reindexer.repository.item.dto.TestNestedItem;

/**
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
@Namespace(name = "items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class TestItem {

	@Id
	private Long id;

	@Reindex(name = "uuid")
	private UUID uuid;

	@Reindex(name = "name", isSparse = true)
	private String name;

	@Reindex(name = "value")
	private String value;

	@Reindex(name = "price")
	private Price price;

	@Enumerated(EnumType.STRING)
	@Reindex(name = "testEnumString")
	private TestEnum testEnumString;

	@Enumerated(EnumType.ORDINAL)
	@Reindex(name = "testEnumOrdinal")
	private TestEnum testEnumOrdinal;

	@Reindex(name = "place")
	private Place place;

	@Reindex(name = "places")
	private List<Place> places = new ArrayList<>();

	@Reindex(name = "cities")
	private List<String> cities = new ArrayList<>();

	@Reindex(name = "active")
	private boolean active;

	@Reindex(name = "joinedItemId")
	private Long joinedItemId;

	private List<Long> joinedItemIds = new ArrayList<>();

	@Reindex(name = "nested")
	private TestNestedItem nestedItem;

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemId", joinType = JoinType.LEFT)
	private TestJoinedItem joinedItem;

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemId", lazy = true)
	private TestJoinedItem joinedItemLazy;

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemIds", joinType = JoinType.LEFT, lazy = true)
	private List<TestJoinedItem> joinedItems = new ArrayList<>();

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemIds",
			lookup = "select * from test_joined_items where id in (#{joinedItemIds}) order by id desc")
	private List<TestJoinedItem> joinedItemsReverseOrder = new ArrayList<>();

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemIds", lookup = """
			    select *
			      from test_joined_items
			     where id in (#{joinedItemIds})
			     order by
			           price desc,
			           name asc
			     limit 10
			""", sort = "value, id asc")
	private List<TestJoinedItem> joinedItemsOrderByPriceDescNameValueIdAscLimit10 = new ArrayList<>();

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemIds", lookup = """
			    select *
			      from test_joined_items
			     where id in (#{joinedItemIds})
			     limit 5
			""", sort = "price desc, id")
	private List<TestJoinedItem> joinedItemsOrderByPriceDescIdAscLimit5 = new ArrayList<>();

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(indexName = "joinedItemIds", lazy = true, sort = "price desc, id asc")
	private List<TestJoinedItem> joinedItemsOrderByPriceDescIdAsc = new ArrayList<>();

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(lookup = "#{@testJoinedItemRepository.findAllById(joinedItemIds, #sort)}",
			sort = "price desc, id asc")
	private List<TestJoinedItem> joinedItemsFromRepositoryOrderByPriceDescIdAsc = new ArrayList<>();

	@EqualsAndHashCode.Exclude
	@Transient
	@NamespaceReference(lookup = "#{@testJoinedItemRepository.findAllById(joinedItemIds)}")
	private List<TestJoinedItem> joinedItemsRepository = new ArrayList<>();

	private String localDate;

	private String localDateTime;

	@ValueConverter(LocalDatePropertyValueConverter.class)
	private LocalDate customDate;

	@ValueConverter(LocalTimePropertyValueConverter.class)
	private LocalTime customTime;

	@ValueConverter(LocalDateTimePropertyValueConverter.class)
	private LocalDateTime customDateTime;

	private LocalDate defaultDate;

	private LocalTime defaultTime;

	@Reindex(name = "defaultDateTime")
	private LocalDateTime defaultDateTime;

	public TestItem(Long id, String name, String value) {
		this.id = id;
		this.name = name;
		this.value = value;
	}

	public TestItem(Long id, String name, String value, boolean active) {
		this.id = id;
		this.name = name;
		this.value = value;
		this.active = active;
	}

	public TestItem(Long id, boolean active) {
		this.id = id;
		this.active = active;
	}

	public TestItem(Long id, String name, String value, List<String> cities) {
		this.id = id;
		this.name = name;
		this.value = value;
		this.cities = cities;
	}

	public TestItem(Long id, String name, String value, Price price, Place place, List<Place> places) {
		this.id = id;
		this.name = name;
		this.value = value;
		this.price = price;
		this.place = place;
		this.places = places;
	}

	public TestItem(Long id, String name, String value, TestEnum testEnumString, TestEnum testEnumOrdinal) {
		this.id = id;
		this.name = name;
		this.value = value;
		this.testEnumString = testEnumString;
		this.testEnumOrdinal = testEnumOrdinal;
	}

	public TestItem(Long id, TestNestedItem nestedItem, Long joinedItemId, List<Long> joinedItemIds, String name,
			String value, String localDate, String localDateTime) {
		this.id = id;
		this.nestedItem = nestedItem;
		this.joinedItemId = joinedItemId;
		this.joinedItemIds = joinedItemIds;
		this.name = name;
		this.value = value;
		this.localDate = localDate;
		this.localDateTime = localDateTime;
	}

	public TestItem(Long id, TestNestedItem nestedItem, String name, String value) {
		this.id = id;
		this.nestedItem = nestedItem;
		this.name = name;
		this.value = value;
	}

	public TestItem(Long id, List<Long> joinedItemIds) {
		this.id = id;
		this.joinedItemIds = joinedItemIds;
	}

}
