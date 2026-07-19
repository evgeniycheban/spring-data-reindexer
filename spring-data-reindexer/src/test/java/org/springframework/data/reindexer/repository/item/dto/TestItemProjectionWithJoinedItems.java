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
package org.springframework.data.reindexer.repository.item.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

import lombok.Data;

import org.springframework.data.convert.ValueConverter;
import org.springframework.data.reindexer.repository.item.converter.TestJoinedItemManuallyPropertyValueConverter;
import org.springframework.data.reindexer.repository.item.converter.TestJoinedItemPropertyValueConverter;

/**
 * @author Evgeniy Cheban
 */
@Data
public class TestItemProjectionWithJoinedItems {

	private final Long id;

	private final LocalDate localDate;

	private final LocalDateTime localDateTime;

	private final NestedItemRecord nestedItem;

	@ValueConverter(TestJoinedItemPropertyValueConverter.class)
	private final TestJoinedItemProjection joinedItem;

	@ValueConverter(TestJoinedItemManuallyPropertyValueConverter.class)
	private final TestJoinedItemProjection joinedItemLazy;

	private final Set<TestJoinedItemProjection> joinedItems;

	private final Collection<TestJoinedItemProjection> joinedItemsReverseOrder;

	private final Collection<TestJoinedItemProjection> joinedItemsRepository;

}
