package org.springframework.data.reindexer.repository;

import java.util.List;

import ru.rt.restream.reindexer.CloseableIterator;

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
	 * Returns a {@link CloseableIterator} that can be closed to release Reindexer's resources
	 * by calling {@link CloseableIterator#close()} method.
	 *
	 * @return a {@link CloseableIterator} to use
	 */
	CloseableIterator<T> iterator();

}
