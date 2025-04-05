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

/**
 * An object that holds namespace and source information.
 *
 * @author Evgeniy Cheban
 * @since 1.4
 */
public final class NamespaceReferenceSource {

	private final String namespace;

	private final Object source;

	/**
	 * Creates an instance.
	 * @param namespace the namespace to use
	 * @param source the source to use
	 */
	public NamespaceReferenceSource(String namespace, Object source) {
		this.namespace = namespace;
		this.source = source;
	}

	/**
	 * Returns a namespace.
	 * @return the namespace to use
	 */
	public String getNamespace() {
		return this.namespace;
	}

	/**
	 * Returns a source.
	 * @return the source to use
	 */
	public Object getSource() {
		return this.source;
	}

}
