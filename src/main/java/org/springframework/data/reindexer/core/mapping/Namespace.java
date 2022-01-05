package org.springframework.data.reindexer.core.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
	 *
	 * @return the namespace name to use
	 */
	String name();

	// TODO: Add namespace options from ru.rt.restream.reindexer.NamespaceOptions 

}
