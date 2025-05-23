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

/**
 * Allows direct interaction with the underlying {@code LazyLoadingInterceptor}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 * @see LazyLoadingProxyFactory
 */
public interface LazyLoadingProxy {

	/**
	 * Initializes the proxy and returns the wrapped value.
	 * @return a target object
	 */
	Object getTarget();

	/**
	 * Returns the raw {@literal source} object that defines the reference.
	 * @return can be {@literal null}.
	 */
	Object getSource();

}
