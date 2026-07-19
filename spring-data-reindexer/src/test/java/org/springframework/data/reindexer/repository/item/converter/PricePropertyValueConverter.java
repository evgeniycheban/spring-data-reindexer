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
package org.springframework.data.reindexer.repository.item.converter;

import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.reindexer.core.convert.ReindexerConversionContext;
import org.springframework.data.reindexer.repository.item.dto.Price;

/**
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
public class PricePropertyValueConverter implements PropertyValueConverter<Double, Price, ReindexerConversionContext> {

	@Override
	public Double read(Price source, ReindexerConversionContext context) {
		return source.getValue();
	}

	@Override
	public Price write(Double value, ReindexerConversionContext context) {
		return null;
	}

	@Override
	public Double readNull(ReindexerConversionContext context) {
		return 0.0;
	}

}
