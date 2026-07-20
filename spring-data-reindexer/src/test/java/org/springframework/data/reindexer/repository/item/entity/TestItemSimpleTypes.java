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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.annotation.Id;
import org.springframework.data.reindexer.core.mapping.Namespace;

/**
 * @author Evgeniy Cheban
 */
@Namespace(name = "item_simple_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class TestItemSimpleTypes {

	@Id
	private Long id;

	@Reindex(name = "uuid")
	private UUID uuid;

	@Reindex(name = "big_integer")
	private BigInteger bigInteger;

	@Reindex(name = "big_decimal")
	private BigDecimal bigDecimal;

}
