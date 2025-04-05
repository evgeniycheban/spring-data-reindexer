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
package org.springframework.data.reindexer.core.mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.data.annotation.Reference;

/**
 * Specifies a Namespace to join using index name and join type. Supports both One-to-many
 * and One-to-one relationships.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
@Reference
@Retention(RetentionPolicy.RUNTIME)
public @interface NamespaceReference {

	/**
	 * Represents a namespace name.
	 * @return the namespace name to use
	 */
	String namespace() default "";

	/**
	 * Represents an index name to join.
	 * @return the index name to join
	 */
	String indexName();

	/**
	 * Represents a join type, defaults to {@link JoinType#LEFT}.
	 * @return the join type to use
	 */
	JoinType joinType() default JoinType.LEFT;

	/**
	 * Controls whether the referenced entity should be loaded lazily. This defaults to
	 * {@literal false}.
	 * @return {@literal false} by default
	 */
	boolean lazy() default false;

	/**
	 * Controls whether the referenced entity should be fetched if it is a nested
	 * relationship of the top level entity. This defaults to {@literal false}.
	 * @return {@literal false} by default
	 */
	boolean fetch() default false;

}
