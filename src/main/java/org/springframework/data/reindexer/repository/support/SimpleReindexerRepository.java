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
package org.springframework.data.reindexer.repository.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Query.Condition;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ResultIterator;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.ExampleMatcher.NullHandler;
import org.springframework.data.domain.ExampleMatcher.PropertySpecifier;
import org.springframework.data.domain.ExampleMatcher.PropertySpecifiers;
import org.springframework.data.domain.ExampleMatcher.StringMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.core.convert.ReindexerConverter;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentEntity;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.reindexer.repository.util.PageableUtils;
import org.springframework.data.reindexer.repository.util.QueryUtils;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.Assert;

/**
 * Repository base implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class SimpleReindexerRepository<T, ID> implements ReindexerRepository<T, ID> {

	private static final Log LOGGER = LogFactory.getLog(SimpleReindexerRepository.class);

	private final ReindexerEntityInformation<T, ID> entityInformation;

	private final ReindexerMappingContext mappingContext;

	private final Reindexer reindexer;

	private final Namespace<T> namespace;

	private final ReindexerConverter reindexerConverter;

	/**
	 * Creates an instance.
	 *
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param mappingContext the {@link ReindexerMappingContext} to use
	 * @param reindexer the {@link Reindexer} to use
	 * @param reindexerConverter the {@link ReindexerConverter} to use
	 */
	public SimpleReindexerRepository(ReindexerEntityInformation<T, ID> entityInformation,
			ReindexerMappingContext mappingContext, Reindexer reindexer, ReindexerConverter reindexerConverter) {
		this.entityInformation = entityInformation;
		this.mappingContext = mappingContext;
		this.reindexer = reindexer;
		this.reindexerConverter = reindexerConverter;
		this.namespace = openNamespace(entityInformation, reindexer);
	}

	private TransactionalNamespace<T> openNamespace(ReindexerEntityInformation<T, ID> entityInformation, Reindexer reindexer) {
		Namespace<T> namespace = reindexer.openNamespace(entityInformation.getNamespaceName(),
				entityInformation.getNamespaceOptions(), entityInformation.getJavaType());
		return new TransactionalNamespace<>(namespace);
	}

	@Override
	public <S extends T> S save(S entity) {
		Assert.notNull(entity, "Entity must not be null!");
		if (this.entityInformation.isNew(entity)) {
			this.namespace.insert(entity);
		}
		else {
			this.namespace.upsert(entity);
		}
		return entity;
	}

	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities) {
		Assert.notNull(entities, "The given Iterable of entities must not be null!");
		List<S> result = new ArrayList<>();
		for (S entity : entities) {
			result.add(save(entity));
		}
		return result;
	}

	@Override
	public Optional<T> findById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		Query<T> query = joinedQuery().where(this.entityInformation.getIdFieldName(), Condition.EQ, id);
		return findOne(query, this.entityInformation.getJavaType());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S extends T> Optional<S> findOne(Example<S> example) {
		return (Optional<S>) findOne(withExample(joinedQuery(), example), this.entityInformation.getJavaType());
	}

	private <R> Optional<R> findOne(Query<T> query, Class<R> resultType) {
		return query.findOne().map(e -> projectEntity(e, resultType));
	}

	@Override
	public <S extends T, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
		return queryFunction.apply(new FluentQueryByExample<>(example, null, null, null, Collections.emptyList()));
	}

	@Override
	public boolean existsById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		return query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).exists();
	}

	@Override
	public <S extends T> boolean exists(Example<S> example) {
		return withExample(query(), example).exists();
	}

	@Override
	public List<T> findAll() {
		return findAll(Sort.unsorted());
	}

	@Override
	public List<T> findAll(Sort sort) {
		return findAll(joinedQuery(), this.entityInformation.getJavaType(), sort);
	}

	@Override
	public Page<T> findAll(Pageable pageable) {
		return findAll(joinedQuery(), this.entityInformation.getJavaType(), pageable);
	}

	@Override
	public List<T> findAllById(Iterable<ID> ids) {
		Assert.notNull(ids, "The given Ids of entities not be null!");
		Query<T> query = joinedQuery().where(this.entityInformation.getIdFieldName(), Condition.SET, toSet(ids));
		return findAll(query, this.entityInformation.getJavaType(), Sort.unsorted());
	}

	@Override
	public <S extends T> List<S> findAll(Example<S> example) {
		return findAll(example, Sort.unsorted());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
		return (List<S>) findAll(withExample(joinedQuery(), example), this.entityInformation.getJavaType(), sort);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
		return (Page<S>) findAll(withExample(joinedQuery(), example), this.entityInformation.getJavaType(), pageable);
	}

	private <R> Page<R> findAll(Query<T> query, Class<R> resultType, Pageable pageable) {
		if (pageable.isUnpaged()) {
			return new PageImpl<>(findAll(query, resultType, pageable.getSort()));
		}
		query.limit(pageable.getPageSize()).offset(PageableUtils.getOffsetAsInteger(pageable)).reqTotal();
		try (ResultIterator<T> iterator = withSort(query, pageable.getSort()).execute()) {
			List<R> content = new ArrayList<>();
			while (iterator.hasNext()) {
				content.add(projectEntity(iterator.next(), resultType));
			}
			return PageableExecutionUtils.getPage(content, pageable, iterator::getTotalCount);
		}
	}

	private <R> List<R> findAll(Query<T> query, Class<R> resultType, Sort sort) {
		return streamAll(query, resultType, sort).collect(Collectors.toList());
	}

	private <R> Stream<R> streamAll(Query<T> query, Class<R> resultType, Sort sort) {
		return withSort(query, sort).stream().map(e -> projectEntity(e, resultType));
	}

	private Query<T> withSort(Query<T> query, Sort sort) {
		if (sort.isSorted()) {
			for (Order order : sort) {
				query.sort(order.getProperty(), order.isDescending());
			}
		}
		return query;
	}

	private <R> R projectEntity(T e, Class<R> resultType) {
		EntityProjection<R, T> descriptor = SimpleReindexerRepository.this.reindexerConverter.getProjectionIntrospector()
				.introspect(resultType, SimpleReindexerRepository.this.entityInformation.getJavaType());
		return this.reindexerConverter.project(descriptor, e);
	}

	private Set<ID> toSet(Iterable<? extends ID> ids) {
		Set<ID> result = new HashSet<>();
		ids.forEach(result::add);
		return result;
	}

	@Override
	public Query<T> query() {
		return this.namespace.query();
	}

	@Override
	public long count() {
		return query().count();
	}

	@Override
	public <S extends T> long count(Example<S> example) {
		return withExample(query(), example).count();
	}

	@Override
	public void deleteById(ID id) {
		Assert.notNull(id, "The given id must not be null!");
		query().where(this.entityInformation.getIdFieldName(), Query.Condition.EQ, id).delete();
	}

	@Override
	public void delete(T entity) {
		Assert.notNull(entity, "The given entity must not be null!");
		this.namespace.delete(entity);
	}

	@Override
	public void deleteAllById(Iterable<? extends ID> ids) {
		Assert.notNull(ids, "The given Iterable of ids must not be null!");
		query().where(this.entityInformation.getIdFieldName(), Query.Condition.SET, toSet(ids)).delete();
	}

	@Override
	public void deleteAll(Iterable<? extends T> entities) {
		Assert.notNull(entities, "The given Iterable of entities must not be null!");
		entities.forEach(this::delete);
	}

	@Override
	public void deleteAll() {
		query().delete();
	}

	@SuppressWarnings("unchecked")
	private Query<T> joinedQuery() {
		Query<T> query = query();
		return (Query<T>) QueryUtils.withJoins(query, this.entityInformation.getJavaType(), this.mappingContext, this.reindexer);
	}

	private Query<T> withExample(Query<T> criteria, Example<?> example) {
		Object probe = example.getProbe();
		ExampleMatcher matcher = example.getMatcher();
		PropertySpecifiers propertySpecifiers = matcher.getPropertySpecifiers();
		for (String propertyPath : getPropertyPaths(probe, example.getProbeType(), "")) {
			if (matcher.isIgnoredPath(propertyPath)) {
				continue;
			}
			Object propertyValue = BeanPropertyUtils.getProperty(probe, propertyPath);
			if (propertyValue == null && matcher.getNullHandler() == NullHandler.IGNORE) {
				continue;
			}
			if (propertySpecifiers.hasSpecifierForPath(propertyPath)) {
				PropertySpecifier propertySpecifier = propertySpecifiers.getForPath(propertyPath);
				Object value = propertySpecifier.transformValue(Optional.ofNullable(propertyValue)).orElse(null);
				if (value instanceof String s) {
					StringMatcher stringMatcher = propertySpecifier.getStringMatcher();
					if (stringMatcher == null) {
						if (LOGGER.isTraceEnabled()) {
							LOGGER.trace("No StringMatcher provided for property: " + propertyPath + " defaults to `StringMatcher.DEFAULT`");
						}
						stringMatcher = StringMatcher.DEFAULT;
					}
					switch (stringMatcher) {
						case DEFAULT, EXACT -> criteria.where(propertyPath, Condition.EQ, s);
						case STARTING -> criteria.like(propertyPath, s + "%");
						case ENDING -> criteria.like(propertyPath, "%" + s);
						case CONTAINING -> criteria.like(propertyPath, "%" + s + "%");
						default -> throw new InvalidDataAccessApiUsageException("Unsupported StringMatcher: " + stringMatcher);
					}
				}
				else {
					criteria.where(propertyPath, Condition.EQ, value);
				}
			}
			else {
				criteria.where(propertyPath, Condition.EQ, propertyValue);
			}
			if (matcher.isAnyMatching()) {
				criteria.or();
			}
		}
		return criteria;
	}

	private List<String> getPropertyPaths(Object probe, Class<?> domainClass, String path) {
		List<String> result = new ArrayList<>();
		ReindexerPersistentEntity<?> persistentEntity = this.mappingContext.getRequiredPersistentEntity(domainClass);
		for (ReindexerPersistentProperty property : persistentEntity) {
			if (property.isNamespaceReference() || property.isCollectionLike()) {
				continue;
			}
			if (property.isEntity()) {
				Object value = BeanPropertyUtils.getProperty(probe, property.getName());
				if (value != null) {
					result.addAll(getPropertyPaths(value, property.getType(), path + property.getName() + "."));
				}
			}
			else {
				result.add(path + property.getName());
			}
		}
		return result;
	}

	private final class FluentQueryByExample<E extends T, R> implements FluentQuery.FetchableFluentQuery<R> {

		private final Example<E> example;

		private final Sort sort;

		private final Integer limit;

		private final Class<R> resultType;

		private final List<String> fieldsToInclude;

		private FluentQueryByExample(Example<E> example, Sort sort, Integer limit, Class<R> resultType, List<String> fieldsToInclude) {
			this.example = example;
			this.sort = sort != null ? sort : Sort.unsorted();
			this.limit = limit;
			this.resultType = resultType;
			this.fieldsToInclude = fieldsToInclude;
		}

		@Override
		public FetchableFluentQuery<R> sortBy(Sort sort) {
			return new FluentQueryByExample<>(this.example, sort, this.limit, this.resultType, this.fieldsToInclude);
		}

		@Override
		public <R> FetchableFluentQuery<R> as(Class<R> resultType) {
			return new FluentQueryByExample<>(this.example, this.sort, this.limit, resultType, this.fieldsToInclude);
		}

		@Override
		public FetchableFluentQuery<R> project(Collection<String> properties) {
			return new FluentQueryByExample<>(this.example, this.sort, this.limit, this.resultType, new ArrayList<>(properties));
		}

		@Override
		public R oneValue() {
			return findOne(byExample(joinedQuery()), this.resultType).orElse(null);
		}

		@Override
		public R firstValue() {
			return findOne(byExample(joinedQuery()).limit(1), this.resultType).orElse(null);
		}

		@Override
		public List<R> all() {
			return findAll(byExample(joinedQuery()), this.resultType, this.sort);
		}

		@Override
		public Page<R> page(Pageable pageable) {
			return findAll(withSort(byExample(joinedQuery()), this.sort), this.resultType, pageable);
		}

		@Override
		public Stream<R> stream() {
			return streamAll(byExample(joinedQuery()), this.resultType, this.sort);
		}

		@Override
		public long count() {
			return byExample(query()).count();
		}

		@Override
		public boolean exists() {
			return byExample(query()).exists();
		}

		private Query<T> byExample(Query<T> query) {
			if (this.limit != null) {
				query.limit(this.limit);
			}
			if (!this.fieldsToInclude.isEmpty()) {
				query.select(this.fieldsToInclude.toArray(String[]::new));
			}
			return withExample(query, this.example);
		}

	}

}
