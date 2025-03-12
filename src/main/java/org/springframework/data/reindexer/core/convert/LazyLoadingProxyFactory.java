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
package org.springframework.data.reindexer.core.convert;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.rt.restream.reindexer.exceptions.ReindexerException;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.reindexer.LazyLoadingException;
import org.springframework.data.reindexer.core.mapping.ReindexerPersistentProperty;
import org.springframework.data.util.Lock;
import org.springframework.data.util.Lock.AcquiredLock;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.util.ReflectionUtils;

/**
 * {@link ProxyFactory} to create a proxy for {@link ReindexerPersistentProperty#getType()} to resolve a reference lazily.
 * <strong>NOTE:</strong> This class is intended for internal usage only.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public final class LazyLoadingProxyFactory {

	private static final Log LOGGER = LogFactory.getLog(LazyLoadingProxyFactory.class);

	private final SpringObjenesis objenesis = new SpringObjenesis(null);

	/**
	 * Creates a lazy loading proxy that uses {@literal callback} to fetch an association.
	 *
	 * @param type the target type of the proxy being created to use
	 * @param property the {@link ReindexerPersistentProperty} to use
	 * @param callback the callback to fetch an association
	 * @param source the source of an association to use
	 * @param valueConverter the value converter to use
	 */
	public Object createLazyLoadingProxy(Class<?> type, ReindexerPersistentProperty property, Supplier<Object> callback,
			Object source, Converter<Object, ?> valueConverter) {
		LazyLoadingInterceptor interceptor = new LazyLoadingInterceptor(property, callback, source, valueConverter);
		if (!type.isInterface()) {
			Factory factory = (Factory) this.objenesis.newInstance(getEnhancedTypeFor(type));
			factory.setCallbacks(new Callback[] { interceptor });
			return factory;
		}
		ProxyFactory proxyFactory = prepareFactory(type);
		proxyFactory.addAdvice(interceptor);
		return proxyFactory.getProxy(LazyLoadingProxy.class.getClassLoader());
	}

	private Class<?> getEnhancedTypeFor(Class<?> type) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(type);
		enhancer.setCallbackType(LazyLoadingInterceptor.class);
		enhancer.setInterfaces(new Class[] { LazyLoadingProxy.class });
		enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
		enhancer.setAttemptLoad(true);
		return enhancer.createClass();
	}

	private ProxyFactory prepareFactory(Class<?> targetType) {
		ProxyFactory proxyFactory = new ProxyFactory();
		for (Class<?> type : targetType.getInterfaces()) {
			proxyFactory.addInterface(type);
		}
		proxyFactory.addInterface(LazyLoadingProxy.class);
		proxyFactory.addInterface(targetType);
		return proxyFactory;
	}

	private static class LazyLoadingInterceptor
			implements MethodInterceptor, org.springframework.cglib.proxy.MethodInterceptor, Serializable {

		private static final Method GET_TARGET_METHOD;

		private static final Method GET_SOURCE_METHOD;

		private static final Method FINALIZE_METHOD;

		static {
			try {
				GET_TARGET_METHOD = LazyLoadingProxy.class.getMethod("getTarget");
				GET_SOURCE_METHOD = LazyLoadingProxy.class.getMethod("getSource");
				FINALIZE_METHOD = Object.class.getDeclaredMethod("finalize");
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

		private final Lock readLock = Lock.of(this.rwLock.readLock());

		private final Lock writeLock = Lock.of(this.rwLock.writeLock());

		private final ReindexerPersistentProperty property;

		private final Supplier<Object> callback;

		private final Object source;

		private final Converter<Object, ?> valueConverter;

		private volatile boolean resolved;

		private Object result;

		private LazyLoadingInterceptor(ReindexerPersistentProperty property, Supplier<Object> callback, Object source, Converter<Object, ?> valueConverter) {
			this.property = property;
			this.callback = callback;
			this.source = source;
			this.valueConverter = valueConverter;
		}

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			return intercept(invocation.getThis(), invocation.getMethod(), invocation.getArguments(), null);
		}

		@Override
		public Object intercept(Object o, Method method, Object[] args, MethodProxy proxy) throws Throwable {
			if (GET_TARGET_METHOD.equals(method)) {
				return ensureResolved();
			}
			if (GET_SOURCE_METHOD.equals(method)) {
				return this.source;
			}
			if (ReflectionUtils.isObjectMethod(method) && Object.class.equals(method.getDeclaringClass())) {
				if (ReflectionUtils.isToStringMethod(method)) {
					return proxyToString(this.source);
				}
				if (ReflectionUtils.isEqualsMethod(method)) {
					return proxyEquals(o, args[0]);
				}
				if (ReflectionUtils.isHashCodeMethod(method)) {
					return proxyHashCode();
				}
				if (FINALIZE_METHOD.equals(method)) {
					return null;
				}
			}
			Object target = ensureResolved();
			if (target == null) {
				return null;
			}
			ReflectionUtils.makeAccessible(method);
			return method.invoke(target, args);
		}

		private String proxyToString(Object source) {
			StringBuilder description = new StringBuilder();
			if (source != null) {
				if (source instanceof NamespaceReferenceSource referenceSource) {
					description.append(referenceSource.getNamespace());
					description.append(":");
					description.append(referenceSource.getSource());
				}
				else {
					description.append(source);
				}
			}
			else {
				description.append(0);
			}
			description.append("$").append(LazyLoadingProxy.class.getSimpleName());
			return description.toString();
		}

		private boolean proxyEquals(Object proxy, Object that) {
			if (!(that instanceof LazyLoadingProxy)) {
				return false;
			}
			if (that == proxy) {
				return true;
			}
			return proxyToString(proxy).equals(that.toString());
		}

		private int proxyHashCode() {
			return proxyToString(this.source).hashCode();
		}

		private void writeObject(ObjectOutputStream out) throws IOException {
			ensureResolved();
			out.writeObject(this.result);
		}

		private void readObject(ObjectInputStream in) throws IOException {
			try {
				this.result = in.readObject();
				this.resolved = true;
			} catch (ClassNotFoundException e) {
				throw new LazyLoadingException("Could not deserialize result", e);
			}
		}

		private Object ensureResolved() {
			if (this.resolved) {
				return this.result;
			}
			try (AcquiredLock l = this.readLock.lock()) {
				if (this.resolved) {
					if (LOGGER.isTraceEnabled()) {
						LOGGER.trace(String.format("Accessing already resolved lazy loading property %s.%s",
								this.property.getOwner().getName(), this.property.getName()));
					}
					return this.result;
				}
			}
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace(String.format("Resolving lazy loading property %s.%s",
						this.property.getOwner().getName(), this.property.getName()));
			}
			try (AcquiredLock l = this.writeLock.lock()) {
				if (!this.resolved) {
					this.result = this.valueConverter.convert(this.callback.get());
					this.resolved = true;
				}
				return this.result;
			} catch (ReindexerException e) {
				throw new LazyLoadingException("Unable to lazily resolve reference", e);
			}
		}

	}

}
