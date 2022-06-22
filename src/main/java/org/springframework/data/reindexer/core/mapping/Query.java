package org.springframework.data.reindexer.core.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.data.annotation.Persistent;

/**
 * Annotation to declare SQL-based Reindexer queries directly on repository methods.
 *
 * @author Evgeniy Cheban
 */
@Persistent
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Query {

	/**
	 * An SQL-based Reindexer query to execute.
	 *
	 * @return the SQL-based Reindexer query to execute
	 */
	String value();

}
