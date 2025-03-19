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

import org.springframework.data.convert.EntityConverter;
import org.springframework.data.convert.EntityReader;
import org.springframework.data.convert.EntityWriter;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.projection.EntityProjectionIntrospector;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;

/**
 * Central Reindexer specific converter interface which combines {@link EntityWriter} and {@link EntityReader}.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public interface ReindexerConverter extends EntityConverter<ReindexerPersistentEntity<?>, ReindexerPersistentProperty, Object, Object> {

	/**
	 * Apply a projection to {@link E} and return the projection return type {@code R}.
	 * {@link EntityProjection#isProjection() Non-projecting} descriptors fall back to {@link #read(Class, Object) regular
	 * object materialization}.
	 *
	 * @param entityProjection the projection entity descriptor, must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @param <R> projection type
	 * @param <E> entity type
	 * @return a new instance of the projection return type {@code R}.
	 */
	<R, E> R project(EntityProjection<R, E> entityProjection, E entity);

	/**
	 * Returns a {@link EntityProjectionIntrospector} that introspects the returned type.
	 *
	 * @return the {@link EntityProjectionIntrospector} to introspect the returned type
	 */
	EntityProjectionIntrospector getProjectionIntrospector();

	/**
	 * {@inheritDoc}
	 */
	@Override
	ReindexerMappingContext getMappingContext();

}
