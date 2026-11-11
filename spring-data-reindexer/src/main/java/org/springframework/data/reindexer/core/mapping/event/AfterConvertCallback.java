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
package org.springframework.data.reindexer.core.mapping.event;

import org.springframework.data.mapping.callback.EntityCallback;

/**
 * Callback being invoked after a domain object is converted from a persisted form.
 *
 * @author Evgeniy Cheban
 * @since 1.8
 * @see org.springframework.data.mapping.callback.EntityCallbacks
 */
@FunctionalInterface
public interface AfterConvertCallback<T> extends EntityCallback<T> {

	/**
	 * Entity callback method invoked after a domain object is converted from a persisted
	 * form. Can return either the same or a modified instance of the domain object.
	 * @param entity the domain object (the result of the conversion)
	 * @param namespace name of the namespace
	 * @return the domain object that is the result of reading it from the persisted form
	 */
	T onAfterConvert(T entity, String namespace);

}
