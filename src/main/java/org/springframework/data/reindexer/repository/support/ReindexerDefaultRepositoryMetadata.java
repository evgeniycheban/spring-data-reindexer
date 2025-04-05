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
package org.springframework.data.reindexer.repository.support;

import java.lang.reflect.Method;

import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.util.ReactiveWrapperConverters;
import org.springframework.data.util.TypeInformation;

/**
 * Default implementation of
 * {@link org.springframework.data.repository.core.RepositoryMetadata}. Will inspect
 * generic types of {@link org.springframework.data.repository.Repository} to find out
 * about domain and id class.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerDefaultRepositoryMetadata extends DefaultRepositoryMetadata {

	/**
	 * Creates a new {@link ReindexerDefaultRepositoryMetadata} for the given repository
	 * interface.
	 * @param repositoryInterface must not be {@literal null}.
	 */
	public ReindexerDefaultRepositoryMetadata(Class<?> repositoryInterface) {
		super(repositoryInterface);
	}

	@Override
	public Class<?> getReturnedDomainClass(Method method) {
		TypeInformation<?> returnType = getReturnType(method);
		returnType = ReactiveWrapperConverters.unwrapWrapperTypes(returnType);
		return ReindexerQueryExecutionConverters.unwrapWrapperTypes(returnType, getDomainTypeInformation()).getType();

	}

}
