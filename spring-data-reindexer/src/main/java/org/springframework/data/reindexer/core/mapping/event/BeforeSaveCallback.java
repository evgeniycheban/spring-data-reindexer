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
 * Callback being invoked before a domain object is saved.
 *
 * @author Evgeniy Cheban
 * @since 1.8
 * @see org.springframework.data.mapping.callback.EntityCallbacks
 */
public interface BeforeSaveCallback<T> extends EntityCallback<T> {

	/**
	 * Entity callback method invoked before a domain object is saved. Can return either
	 * the same or a modified instance of the domain object. This method is called after
	 * converting a domain object. Use the {@link BeforeConvertCallback} to change the
	 * domain object before being converted.
	 * @param entity the domain object to save
	 * @param namespace name of the namespace
	 * @return the domain object to be persisted
	 */
	T onBeforeSave(T entity, String namespace);

}
