package org.springframework.data.reindexer.repository.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 */
final class ReindexerEntityInformationFactory {

	private static final Map<Class<?>, ReindexerEntityInformation<?, ?>> CACHE = new ConcurrentHashMap<>();

	@SuppressWarnings("unchecked")
	static <T, ID> ReindexerEntityInformation<T, ID> getReindexerEntityInformation(Class<T> domainClass) {
		return (ReindexerEntityInformation<T, ID>) CACHE.computeIfAbsent(domainClass, MappingReindexerEntityInformation::new);
	}

	private ReindexerEntityInformationFactory() {
	}

}
