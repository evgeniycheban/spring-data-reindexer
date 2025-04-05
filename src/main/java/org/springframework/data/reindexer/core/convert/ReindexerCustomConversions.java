/*
 * Copyright 2022 evgeniycheban
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.reindexer.core.convert;

import java.util.Collection;
import java.util.Collections;

import org.springframework.data.convert.CustomConversions;

/**
 * Value object to capture custom conversion. {@link ReindexerCustomConversions} also acts as a factory for
 * {@link org.springframework.data.mapping.model.SimpleTypeHolder}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 * @see org.springframework.data.convert.CustomConversions
 * @see org.springframework.data.mapping.model.SimpleTypeHolder
 */
public class ReindexerCustomConversions extends CustomConversions {

	public ReindexerCustomConversions() {
		super(new ConverterConfiguration(StoreConversions.NONE, Collections.emptyList()));
	}

	public ReindexerCustomConversions(StoreConversions storeConversions, Collection<?> converters) {
		super(storeConversions, converters);
	}
}
