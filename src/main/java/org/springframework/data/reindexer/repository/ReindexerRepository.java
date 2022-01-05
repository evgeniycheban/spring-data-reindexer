package org.springframework.data.reindexer.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Reindexer-specific {@link Repository} interface.
 * @author Evgeniy Cheban
 */
@NoRepositoryBean
public interface ReindexerRepository<T, ID> extends CrudRepository<T, ID> {

	@Override
	<S extends T> List<S> saveAll(Iterable<S> entities);

	@Override
	List<T> findAll();

}
