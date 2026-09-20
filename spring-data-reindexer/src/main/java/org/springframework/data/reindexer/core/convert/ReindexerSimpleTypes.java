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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ru.rt.restream.reindexer.FieldType;

import org.springframework.data.mapping.model.SimpleTypeHolder;

/**
 * Simple constant holder for a {@link SimpleTypeHolder} enriched with Reindexer specific
 * simple types.
 *
 * @author Evgeniy Cheban
 * @since 1.7
 */
public final class ReindexerSimpleTypes {

	private static final Set<Class<?>> REINDEXER_SIMPLE_TYPES = Set.of(UUID.class);

	/**
	 * A {@link SimpleTypeHolder} enriched with Reindexer specific simple types.
	 */
	public static final SimpleTypeHolder HOLDER = new SimpleTypeHolder(REINDEXER_SIMPLE_TYPES, true);

	/**
	 * Mapped Java types to internal Reindexer types.
	 */
	// @formatter:off
	public static final Map<Class<?>, FieldType> MAPPED_TYPES = Map.ofEntries(
			Map.entry(boolean.class, FieldType.BOOL),
			Map.entry(Boolean.class, FieldType.BOOL),
			Map.entry(byte.class, FieldType.INT),
			Map.entry(Byte.class, FieldType.INT),
			Map.entry(short.class, FieldType.INT),
			Map.entry(Short.class, FieldType.INT),
			Map.entry(int.class, FieldType.INT),
			Map.entry(Integer.class, FieldType.INT),
			Map.entry(long.class, FieldType.INT64),
			Map.entry(Long.class, FieldType.INT64),
			Map.entry(float.class, FieldType.FLOAT),
			Map.entry(Float.class, FieldType.FLOAT),
			Map.entry(double.class, FieldType.DOUBLE),
			Map.entry(Double.class, FieldType.DOUBLE),
			Map.entry(String.class, FieldType.STRING),
			Map.entry(char.class, FieldType.STRING),
			Map.entry(Character.class, FieldType.STRING),
			Map.entry(BigDecimal.class, FieldType.STRING),
			Map.entry(BigInteger.class, FieldType.STRING),
			Map.entry(UUID.class, FieldType.UUID)
	);
	// @formatter:on

	private ReindexerSimpleTypes() {
	}

}
