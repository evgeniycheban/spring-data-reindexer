package org.springframework.data.reindexer.repository.query;

import ru.rt.restream.reindexer.NamespaceOptions;

import org.springframework.data.repository.core.EntityInformation;

/**
 * Reindexer-specific {@link EntityInformation}.
 *
 * @author Evgeniy Cheban
 */
public interface ReindexerEntityInformation<T, ID> extends EntityInformation<T, ID> {

	/**
	 * Returns the name of the namespace the entity shall be persisted to.
	 *
	 * @return the name of the namespace the entity shall be persisted to
	 */
	String getNamespaceName();

	/**
	 * Returns a {@link NamespaceOptions}.
	 *
	 * @return the {@link NamespaceOptions} to use
	 */
	NamespaceOptions getNamespaceOptions();

	/**
	 * Returns the field that the id will be persisted to.
	 *
	 * @return the field that the id will be persisted to
	 */
	String getIdFieldName();

}
