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
package org.springframework.data.reindexer.core.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import ru.rt.restream.reindexer.NamespaceOptions;

import org.springframework.data.annotation.Persistent;

/**
 * Identifies a domain object to be persisted to Reindexer.
 *
 * @author Evgeniy Cheban
 */
@Persistent
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Namespace {

	/**
	 * The namespace name the index representing the entity is supposed to be stored in.
	 * @return the namespace name to use
	 */
	String name();

	/**
	 * Only in memory namespace. Defaults to
	 * {@link NamespaceOptions#DEFAULT_ENABLE_STORAGE}
	 * @return true, if only in memory namespace
	 */
	boolean enableStorage() default NamespaceOptions.DEFAULT_ENABLE_STORAGE;

	/**
	 * Create item storage if missing. Defaults to
	 * {@link NamespaceOptions#DEFAULT_CREATE_IF_MISSING}
	 * @return true, if create item storage if missing
	 */
	boolean createStorageIfMissing() default NamespaceOptions.DEFAULT_CREATE_IF_MISSING;

	/**
	 * Drop ns on index mismatch error. Defaults to
	 * {@link NamespaceOptions#DEFAULT_DROP_ON_INDEX_CONFLICT}
	 * @return true, if drop ns on index mismatch error
	 */
	boolean dropOnIndexesConflict() default NamespaceOptions.DEFAULT_DROP_ON_INDEX_CONFLICT;

	/**
	 * Drop on file errors. Defaults to
	 * {@link NamespaceOptions#DEFAULT_DROP_ON_FILE_FORMAT_ERROR}
	 * @return true, if drop on file errors
	 */
	boolean dropOnFileFormatError() default NamespaceOptions.DEFAULT_DROP_ON_FILE_FORMAT_ERROR;

	/**
	 * Disable object cache. Defaults to
	 * {@link NamespaceOptions#DEFAULT_DISABLE_OBJ_CACHE}
	 * @return true, if disable object cache
	 */
	boolean disableObjCache() default NamespaceOptions.DEFAULT_DISABLE_OBJ_CACHE;

	/**
	 * Object cache items count. Defaults to
	 * {@link NamespaceOptions#DEFAULT_OBJ_CACHE_ITEMS_COUNT}
	 * @return object cache items count
	 */
	long objCacheItemsCount() default NamespaceOptions.DEFAULT_OBJ_CACHE_ITEMS_COUNT;

}
