package org.springframework.data.reindexer.repository.support;

import java.io.Serializable;

import ru.rt.restream.reindexer.Reindexer;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

/**
 * A {@link FactoryBean} to create {@link ReindexerRepository} instances.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
		extends RepositoryFactoryBeanSupport<T, S, ID> {

	private Reindexer reindexer;

	/**
	 * Creates an instance.
	 *
	 * @param repositoryInterface the repository interface to use
	 */
	public ReindexerRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
		super(repositoryInterface);
	}

	/**
	 * Sets the {@link Reindexer}.
	 *
	 * @param reindexer the {@link Reindexer} to use
	 */
	public void setReindexer(Reindexer reindexer) {
		this.reindexer = reindexer;
	}

	@Override
	protected RepositoryFactorySupport createRepositoryFactory() {
		return new ReindexerRepositoryFactory(this.reindexer);
	}

	@Override
	public void afterPropertiesSet() {
		Assert.state(this.reindexer != null,
				"Reindexer instance is not configured. Consider add Reindexer @Bean to the ApplicationContext");
		super.afterPropertiesSet();
	}

}
