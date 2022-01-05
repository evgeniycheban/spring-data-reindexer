package org.springframework.data.reindexer.repository.support;

import java.lang.reflect.Field;

import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.util.Assert;

/**
 * {@link ReindexerEntityInformation} implementation using a domain class to lookup the necessary
 * information.
 *
 * @author Evgeniy Cheban
 */
public class MappingReindexerEntityInformation<T, ID> implements ReindexerEntityInformation<T, ID> {

	private final Class<T> domainClass;

	private final Field idField;

	private final String namespaceName;

	/**
	 * Creates an instance.
	 *
	 * @param domainClass the domain class to use
	 */
	public MappingReindexerEntityInformation(Class<T> domainClass) {
		this.domainClass = domainClass;
		this.idField = getIdField(domainClass);
		this.namespaceName = getNamespaceName(domainClass);
	}

	private Field getIdField(Class<T> domainClass) {
		Field idField = ReflectionUtils.findField(domainClass, new ReflectionUtils.DescribedFieldFilter() {
			@Override
			public String getDescription() {
				return "Found more than one field with @Reindex(isPrimaryKey = true) in " + domainClass;
			}

			@Override
			public boolean matches(Field field) {
				Reindex reindexAnnotation = field.getAnnotation(Reindex.class);
				return reindexAnnotation != null && reindexAnnotation.isPrimaryKey();
			}
		}, true);
		Assert.notNull(idField, () -> "ID is not found consider add @Reindex(isPrimaryKey = true) field to " + domainClass);
		org.springframework.util.ReflectionUtils.makeAccessible(idField);
		return idField;
	}

	private String getNamespaceName(Class<T> domainClass) {
		Namespace namespaceAnnotation = domainClass.getAnnotation(Namespace.class);
		Assert.notNull(namespaceAnnotation, () -> "@Namespace annotation is not found on " + domainClass);
		return namespaceAnnotation.name();
	}

	@Override
	public String getNamespaceName() {
		return this.namespaceName;
	}

	@Override
	public String getIdFieldName() {
		return this.idField.getName();
	}

	@Override
	public boolean isNew(T entity) {
		return getIdValue(entity) == null;
	}

	@Override
	public ID getId(T entity) {
		return getIdValue(entity);
	}

	@SuppressWarnings("unchecked")
	private ID getIdValue(T entity) {
		return (ID) org.springframework.util.ReflectionUtils.getField(this.idField, entity);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<ID> getIdType() {
		return (Class<ID>) this.idField.getType();
	}

	@Override
	public Class<T> getJavaType() {
		return this.domainClass;
	}

}
