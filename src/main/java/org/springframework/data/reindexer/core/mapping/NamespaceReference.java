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
	String indexName() default "";

	/**
	 * Defines a custom lookup query to fetch namespace reference. The query can contain
	 * SpEL expression that refers to application or aggregate root's context:
	 * <p>
	 * {@code select * from joined_items where id in #{joinedItemIds} order by id desc}.
	 * </p>
	 * Alternatively you can use SpEL expression to fetch namespace reference by calling a
	 * spring-managed bean, for example you can directly call repository method to
	 * retrieve necessary data:
	 * <p>
	 * {@code #{@joinedItemRepository.findAllById(joinedItemIds)}}.
	 * </p>
	 * @return the custom lookup query to fetch namespace reference
	 * @since 1.5
	 */
	String lookup() default "";

	/**
	 * Defines a specific sort orders to be applied to the target query.
	 * <p>
	 * If the {@link #lookup()} query already defines ORDER BY clause the orders will be
	 * merged with the ones specified in this attribute: <pre>
	 * &#064;NamespaceReference(indexName  = "joinedItemIds", lookup = """
	 *             select *
	 *               from joined_items
	 *              where id in (#{joinedItemIds})
	 *              order by
	 *                    price desc,
	 *                    name asc
	 *              limit 10
	 *         """, sort = "value, id asc")
	 * </pre> it will be rewritten as follows:<pre>
	 *  select *
	 *    from joined_items
	 *   where id in (#{joinedItemIds})
	 *   order by
	 *         price desc,
	 *         name asc,
	 *         value,
	 *         id asc
	 *  limit 10
	 * </pre> if ORDER BY clause is not defined in the {@link #lookup()} query, the
	 * specified orders will be appended at the end of the query string within ORDER BY
	 * clause.
	 * <p>
	 * You can use the sort object in the SpEL expression by using reference #sort to
	 * access it, the target type of sort object is
	 * {@link org.springframework.data.domain.Sort} this object can be passed to the
	 * method invocation within the expression:
	 * <p>
	 * {@code #{@joinedItemRepository.findAllById(joinedItemIds, #sort)}}
	 * </p>
	 * @since 1.5
	 */
	String sort() default "";

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
