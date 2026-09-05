/*
 * Copyright 2022-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.reindexer.repository.util;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.reindexer.core.mapping.JoinType;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.binding.Consts;

import org.springframework.data.annotation.Id;
import org.springframework.data.reindexer.core.convert.ReindexerSimpleTypes;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.support.ReindexerNamespaceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link QueryUtils}.
 *
 * @author Evgeniy Cheban
 */
@ExtendWith(MockitoExtension.class)
class QueryUtilsTests {

	final ReindexerMappingContext mappingContext = createMappingContext();

	@Mock
	ReindexerNamespaceFactory namespaceFactory;

	@Mock
	ReindexerNamespace<Person> personNamespace;

	@Mock
	ReindexerNamespace<Account> accountNamespace;

	@Mock
	ReindexerNamespace<Role> roleNamespace;

	@Mock
	ReindexerNamespace<Address> addressNamespace;

	@Mock
	ReindexerNamespace<Country> countryNamespace;

	@Mock(answer = Answers.RETURNS_SELF)
	Query<Person> personQuery;

	@Mock(answer = Answers.RETURNS_SELF)
	Query<Account> accountQuery;

	@Mock(answer = Answers.RETURNS_SELF)
	Query<Role> roleQuery;

	@Mock(answer = Answers.RETURNS_SELF)
	Query<Address> addressQuery;

	@Mock(answer = Answers.RETURNS_SELF)
	Query<Country> countryQuery;

	static ReindexerMappingContext createMappingContext() {
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		mappingContext.setSimpleTypeHolder(ReindexerSimpleTypes.HOLDER);
		return mappingContext;
	}

	@Test
	void withJoins() {
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.namespaceFactory.openNamespace(Person.class)).thenReturn(this.personNamespace);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		when(this.personNamespace.query()).thenReturn(this.personQuery);
		Query<?> joinedQuery = QueryUtils.withJoins(this.personQuery, Person.class, this.mappingContext,
				this.namespaceFactory);
		assertThat(joinedQuery).isSameAs(this.personQuery);
		InOrder inOrder = inOrder(this.namespaceFactory, this.accountNamespace, this.addressNamespace,
				this.personNamespace, this.accountQuery, this.addressQuery, this.personQuery);
		inOrder.verify(this.namespaceFactory).openNamespace(Account.class);
		inOrder.verify(this.accountNamespace).query();
		inOrder.verify(this.accountQuery).on("id", Query.Condition.EQ, "person_id");
		inOrder.verify(this.personQuery).leftJoin(this.accountQuery, "accounts");
		inOrder.verify(this.namespaceFactory).openNamespace(Address.class);
		inOrder.verify(this.addressNamespace).query();
		inOrder.verify(this.addressQuery).on("address_id", Query.Condition.EQ, "id");
		inOrder.verify(this.personQuery).innerJoin(this.addressQuery, "address");
		inOrder.verify(this.namespaceFactory).openNamespace(Person.class);
		inOrder.verify(this.personNamespace).query();
		inOrder.verify(this.personQuery).on("manager_id", Query.Condition.EQ, "id");
		inOrder.verify(this.personQuery).leftJoin(this.personQuery, "manager");
		inOrder.verifyNoMoreInteractions();
		// V1 does not support nested joins.
		verifyNoInteractions(this.roleNamespace, this.countryNamespace, this.roleQuery, this.countryQuery);
	}

	@Test
	void withInnerJoins() {
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		Query<?> joinedQuery = QueryUtils.withInnerJoins(this.personQuery, Person.class, this.mappingContext,
				this.namespaceFactory);
		assertThat(joinedQuery).isSameAs(this.personQuery);
		InOrder inOrder = inOrder(this.namespaceFactory, this.addressNamespace, this.addressQuery, this.personQuery);
		inOrder.verify(this.namespaceFactory).openNamespace(Address.class);
		inOrder.verify(this.addressNamespace).query();
		inOrder.verify(this.addressQuery).on("address_id", Query.Condition.EQ, "id");
		inOrder.verify(this.personQuery).innerJoin(this.addressQuery, "address");
		inOrder.verifyNoMoreInteractions();
		// Only inner joins are applied.
		verifyNoInteractions(this.accountNamespace, this.roleNamespace, this.countryNamespace, this.personNamespace,
				this.countryQuery);
	}

	@Test
	void withInnerJoinsUsesQueryFormatVersion2() {
		this.mappingContext.setQueryFormatVersion(() -> Consts.QUERY_FORMAT_V2);
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.namespaceFactory.openNamespace(Country.class)).thenReturn(this.countryNamespace);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		when(this.countryNamespace.query()).thenReturn(this.countryQuery);
		Query<?> joinedQuery = QueryUtils.withInnerJoins(this.personQuery, Person.class, this.mappingContext,
				this.namespaceFactory);
		assertThat(joinedQuery).isSameAs(this.personQuery);
		InOrder inOrder = inOrder(this.namespaceFactory, this.addressNamespace, this.countryNamespace,
				this.addressQuery, this.countryQuery, this.personQuery);
		inOrder.verify(this.namespaceFactory).openNamespace(Address.class);
		inOrder.verify(this.addressNamespace).query();
		inOrder.verify(this.namespaceFactory).openNamespace(Country.class);
		inOrder.verify(this.countryNamespace).query();
		inOrder.verify(this.countryQuery).on("country_id", Query.Condition.EQ, "id");
		inOrder.verify(this.addressQuery).innerJoin(this.countryQuery, "country");
		inOrder.verify(this.addressQuery).on("address_id", Query.Condition.EQ, "id");
		inOrder.verify(this.personQuery).innerJoin(this.addressQuery, "address");
		inOrder.verifyNoMoreInteractions();
		// Only inner joins are applied.
		verifyNoInteractions(this.accountNamespace, this.roleNamespace, this.personNamespace);
	}

	@Test
	void withJoinsUsesQueryFormatVersion2() {
		this.mappingContext.setQueryFormatVersion(() -> Consts.QUERY_FORMAT_V2);
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.namespaceFactory.openNamespace(Country.class)).thenReturn(this.countryNamespace);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		when(this.countryNamespace.query()).thenReturn(this.countryQuery);
		Query<?> joinedQuery = QueryUtils.withJoins(this.personQuery, Person.class, this.mappingContext,
				this.namespaceFactory);
		assertThat(joinedQuery).isSameAs(this.personQuery);
		// V2 supports nested joins e.g., person.address.country
		InOrder inOrder = inOrder(this.namespaceFactory, this.accountNamespace, this.roleNamespace,
				this.addressNamespace, this.countryNamespace, this.accountQuery, this.roleQuery, this.addressQuery,
				this.countryQuery, this.personQuery);
		inOrder.verify(this.namespaceFactory).openNamespace(Account.class);
		inOrder.verify(this.accountNamespace).query();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleNamespace).query();
		inOrder.verify(this.roleQuery).on("role_ids", Query.Condition.SET, "id");
		inOrder.verify(this.accountQuery).leftJoin(this.roleQuery, "roles");
		inOrder.verify(this.accountQuery).on("id", Query.Condition.EQ, "person_id");
		inOrder.verify(this.personQuery).leftJoin(this.accountQuery, "accounts");
		inOrder.verify(this.namespaceFactory).openNamespace(Address.class);
		inOrder.verify(this.addressNamespace).query();
		inOrder.verify(this.namespaceFactory).openNamespace(Country.class);
		inOrder.verify(this.countryNamespace).query();
		inOrder.verify(this.countryQuery).on("country_id", Query.Condition.EQ, "id");
		inOrder.verify(this.addressQuery).innerJoin(this.countryQuery, "country");
		inOrder.verify(this.addressQuery).on("address_id", Query.Condition.EQ, "id");
		inOrder.verify(this.personQuery).innerJoin(this.addressQuery, "address");
		inOrder.verifyNoMoreInteractions();
		// Self-joins are fetched lazily using proxies.
		verifyNoInteractions(this.personNamespace);
	}

	@Namespace(name = "persons")
	static class Person {

		@Id
		Long id;

		@Reindex(name = "manager_id")
		Long managerId;

		@Reindex(name = "address_id")
		Long addressId;

		@NamespaceReference(indexName = "id", referencedIndexName = "person_id")
		List<Account> accounts;

		@NamespaceReference(indexName = "address_id", joinType = JoinType.INNER)
		Address address;

		@NamespaceReference(indexName = "manager_id", joinType = JoinType.LEFT)
		Person manager;

	}

	@Namespace(name = "accounts")
	static class Account {

		@Id
		Long id;

		@Reindex(name = "person_id")
		Long personId;

		@Reindex(name = "role_ids")
		List<Long> roleIds;

		String number;

		@NamespaceReference(indexName = "role_ids")
		List<Role> roles;

	}

	@Namespace(name = "roles")
	static class Role {

		@Id
		Long id;

		String name;

	}

	@Namespace(name = "addresses")
	public static class Address {

		@Id
		Long id;

		@Reindex(name = "country_id")
		Long countryId;

		UUID uuid;

		@NamespaceReference(indexName = "country_id", joinType = JoinType.INNER)
		Country country;

	}

	@Namespace(name = "countries")
	static class Country {

		@Id
		Long id;

		String name;

	}

}
