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
package org.springframework.boot.autoconfigure.data.reindexer.person;

import ru.rt.restream.reindexer.annotations.Reindex;

import org.springframework.data.reindexer.core.mapping.Namespace;

/**
 * @author Evgeniy Cheban
 */
@Namespace(name = "persons")
public class Person {

	@Reindex(name = "id", isPrimaryKey = true)
	private Long id;

	@Reindex(name = "name")
	private String name;

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Person{" + "id=" + this.id + ", name='" + this.name + '\'' + '}';
	}

}
