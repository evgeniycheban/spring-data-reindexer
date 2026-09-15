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
package org.springframework.data.reindexer.core.convert;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.StringUtils;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class SortUtils {

	private static final ConcurrentLruCache<String, Sort> SORT_CACHE = new ConcurrentLruCache<>(64,
			SortUtils::parseSort);

	static Sort getSort(String sortString) {
		return SORT_CACHE.get(sortString);
	}

	private static Sort parseSort(String sortString) {
		if (!StringUtils.hasText(sortString)) {
			return Sort.unsorted();
		}
		List<Order> orders = new ArrayList<>();
		for (String part : sortString.split(",")) {
			String[] order = part.trim().split("\\s");
			Assert.isTrue(order.length == 1 || order.length == 2, () -> "Invalid sort order format: " + sortString);
			String property = order[0];
			Direction direction = order.length == 2 ? Direction.fromString(order[1]) : null;
			orders.add(new Order(direction, property));
		}
		return Sort.by(orders);
	}

	private SortUtils() {
	}

}
