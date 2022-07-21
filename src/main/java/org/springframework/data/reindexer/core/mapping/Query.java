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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.data.annotation.QueryAnnotation;

/**
 * Annotation to declare SQL-based Reindexer queries directly on repository methods.
 *
 * @author Evgeniy Cheban
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@QueryAnnotation
public @interface Query {

	/**
	 * An SQL-based Reindexer query to execute.
	 *
	 * @return the SQL-based Reindexer query to execute
	 */
	String value();

	/**
	 * Indicates whether query is for UPDATE or not.
	 *
	 * @return true, if query is for UPDATE. Default is false
	 */
	boolean update() default false;

}
