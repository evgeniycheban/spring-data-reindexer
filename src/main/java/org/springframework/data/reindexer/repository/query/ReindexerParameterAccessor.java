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
package org.springframework.data.reindexer.repository.query;

import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerParameterAccessor extends ParametersParameterAccessor {

	/**
	 * Creates a new {@link ParametersParameterAccessor}.
	 * @param parameters must not be {@literal null}.
	 * @param values must not be {@literal null}.
	 */
	ReindexerParameterAccessor(Parameters<?, ?> parameters, Object[] values) {
		super(parameters, values);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Object[] getValues() {
		return super.getValues();
	}

}
