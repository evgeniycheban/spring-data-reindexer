package org.springframework.data.reindexer.repository;

import java.util.List;

import ru.rt.restream.reindexer.Query;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Reindexer-specific {@link Repository} interface.
 *
 * @author Evgeniy Cheban
 */
@NoRepositoryBean
public interface ReindexerRepository<T, ID> extends CrudRepository<T, ID> {

	@Override
	<S extends T> List<S> saveAll(Iterable<S> entities);

	@Override
	List<T> findAll();

	@Override
	List<T> findAllById(Iterable<ID> ids);

	/**
	 * Returns a new {@link Query} instance for further customizations.
	 * @see Query for more information regarding supported conditions and result types.
	 *
	 * @return the {@link Query} for further customizations
	 */
	Query<T> query();

}
