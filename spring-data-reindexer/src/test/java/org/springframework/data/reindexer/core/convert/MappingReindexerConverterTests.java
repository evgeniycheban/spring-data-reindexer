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
package org.springframework.data.reindexer.core.convert;

import java.util.List;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerNamespace;
import ru.rt.restream.reindexer.annotations.Reindex;
import ru.rt.restream.reindexer.binding.Consts;

import org.springframework.data.annotation.Id;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.NamespaceReference;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.support.ReindexerNamespaceFactory;
import org.springframework.data.reindexer.util.ListBackedResultIterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MappingReindexerConverter}.
 *
 * @author Evgeniy Cheban
 */
@ExtendWith(MockitoExtension.class)
class MappingReindexerConverterTests {

	@Mock
	Reindexer reindexer;

	@Spy
	final ReindexerMappingContext mappingContext = new ReindexerMappingContext();

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

	@InjectMocks
	MappingReindexerConverter converter;

	static Person createManagerPerson() {
		Country country = new Country();
		country.setId(1L);
		country.setName("USA");
		Address address = new Address();
		address.setId(1L);
		address.setCountryId(1L);
		address.setCountry(country);
		Person person = new Person();
		person.setId(1L);
		person.setAddressId(1L);
		person.setFirstName("Alex");
		person.setLastName("Jones");
		person.setAddress(address);
		return person;
	}

	static Person createPerson() {
		Country country = new Country();
		country.setId(2L);
		country.setName("Germany");
		Address address = new Address();
		address.setId(2L);
		address.setCountryId(2L);
		address.setCountry(country);
		Person person = new Person();
		person.setId(2L);
		person.setManagerId(1L);
		person.setAddressId(2L);
		person.setFirstName("John");
		person.setLastName("Smith");
		person.setAddress(address);
		return person;
	}

	static Role createAdminRole() {
		Role role = new Role();
		role.setId(1L);
		role.setName("admin");
		return role;
	}

	static Role createUserRole() {
		Role role = new Role();
		role.setId(2L);
		role.setName("user");
		return role;
	}

	static Account createManagerAccount() {
		Account account = new Account();
		account.setId(1L);
		account.setPersonId(1L);
		account.setNumber("1");
		account.setType("manager");
		account.setRoleIds(List.of(1L));
		return account;
	}

	static Account createPersonalAccount() {
		Account account = new Account();
		account.setId(2L);
		account.setPersonId(2L);
		account.setNumber("2");
		account.setType("personal");
		account.setRoleIds(List.of(1L, 2L));
		return account;
	}

	static Account createBusinessAccount() {
		Account account = new Account();
		account.setId(3L);
		account.setPersonId(2L);
		account.setNumber("3");
		account.setType("business");
		account.setRoleIds(List.of(2L));
		return account;
	}

	@SuppressWarnings("unchecked")
	@Test
	void projectRecordDto() {
		Role adminRole = createAdminRole();
		Role userRole = createUserRole();
		Person managerPerson = createManagerPerson();
		Person person = createPerson();
		person.setManager(managerPerson);
		Account managerAccount = createManagerAccount();
		Account personalAccount = createPersonalAccount();
		Account businessAccount = createBusinessAccount();
		EntityProjection<PersonDto, Person> projection = this.converter.getProjectionIntrospector()
			.introspect(PersonDto.class, Person.class);
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		// @formatter:off
		when(this.accountQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(managerAccount),
						new ListBackedResultIterator<>(personalAccount, businessAccount)
				);
		when(this.roleQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(adminRole),
						new ListBackedResultIterator<>(adminRole, userRole),
						new ListBackedResultIterator<>(userRole)
				);
		// @formatter:on
		PersonDto result = this.converter.project(projection, person);
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(2L);
		assertThat(result.getFirstName()).isEqualTo("John");
		assertThat(result.getLastName()).isEqualTo("Smith");
		PersonDto manager = result.getManager();
		assertThat(manager).isNotNull();
		assertThat(manager).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(manager.getId()).isEqualTo(1L);
		assertThat(manager.getFirstName()).isEqualTo("Alex");
		assertThat(manager.getLastName()).isEqualTo("Jones");
		assertThat(manager.getManager()).isNull();
		List<AccountRecordDto> managerAccounts = manager.getAccounts();
		assertThat(managerAccounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAccounts).hasSize(1);
		assertThat(managerAccounts).element(0).satisfies(account -> {
			assertThat(account.id()).isEqualTo(1L);
			assertThat(account.number()).isEqualTo("1");
			assertThat(account.type()).isEqualTo("manager");
			List<RoleRecordDto> roles = account.roles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.id()).isEqualTo(1L);
				assertThat(role.name()).isEqualTo("admin");
			});
		});
		AddressRecordDto managerAddress = manager.getAddress();
		assertThat(managerAddress).isNotNull();
		assertThat(managerAddress).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddress.id()).isEqualTo(1L);
		CountryRecordDto managerAddressCountry = managerAddress.country();
		assertThat(managerAddressCountry).isNotNull();
		assertThat(managerAddressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddressCountry.id()).isEqualTo(1L);
		assertThat(managerAddressCountry.name()).isEqualTo("USA");
		List<AccountRecordDto> accounts = result.getAccounts();
		assertThat(accounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(accounts).hasSize(2);
		assertThat(accounts).element(0).satisfies(account -> {
			assertThat(account.id()).isEqualTo(2L);
			assertThat(account.number()).isEqualTo("2");
			assertThat(account.type()).isEqualTo("personal");
			List<RoleRecordDto> roles = account.roles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(2);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.id()).isEqualTo(1L);
				assertThat(role.name()).isEqualTo("admin");
			});
			assertThat(roles).element(1).satisfies(role -> {
				assertThat(role.id()).isEqualTo(2L);
				assertThat(role.name()).isEqualTo("user");
			});
		});
		assertThat(accounts).element(1).satisfies(account -> {
			assertThat(account.id()).isEqualTo(3L);
			assertThat(account.number()).isEqualTo("3");
			assertThat(account.type()).isEqualTo("business");
			List<RoleRecordDto> roles = account.roles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.id()).isEqualTo(2L);
				assertThat(role.name()).isEqualTo("user");
			});
		});
		AddressRecordDto address = result.getAddress();
		assertThat(address).isNotNull();
		assertThat(address).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(address.id()).isEqualTo(2L);
		CountryRecordDto addressCountry = address.country();
		assertThat(addressCountry).isNotNull();
		assertThat(addressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(addressCountry.id()).isEqualTo(2L);
		assertThat(addressCountry.name()).isEqualTo("Germany");
		verifyQueries();
	}

	@SuppressWarnings("unchecked")
	@Test
	void projectEntity() {
		Role adminRole = createAdminRole();
		Role userRole = createUserRole();
		Person managerPerson = createManagerPerson();
		Person person = createPerson();
		person.setManager(managerPerson);
		Account managerAccount = createManagerAccount();
		Account personalAccount = createPersonalAccount();
		Account businessAccount = createBusinessAccount();
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		// @formatter:off
		when(this.accountQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(managerAccount),
						new ListBackedResultIterator<>(personalAccount, businessAccount)
				);
		when(this.roleQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(adminRole),
						new ListBackedResultIterator<>(adminRole, userRole),
						new ListBackedResultIterator<>(userRole)
				);
		// @formatter:on
		Person result = this.converter.read(Person.class, person);
		assertThat(result).isSameAs(person);
		assertPerson(result);
		verifyQueries();
	}

	@SuppressWarnings("unchecked")
	@Test
	void readEntity() {
		Role adminRole = createAdminRole();
		Role userRole = createUserRole();
		Person managerPerson = createManagerPerson();
		Person person = createPerson();
		person.setManager(managerPerson);
		Account managerAccount = createManagerAccount();
		Account personalAccount = createPersonalAccount();
		Account businessAccount = createBusinessAccount();
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		// @formatter:off
		when(this.accountQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(managerAccount),
						new ListBackedResultIterator<>(personalAccount, businessAccount)
				);
		when(this.roleQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(adminRole),
						new ListBackedResultIterator<>(adminRole, userRole),
						new ListBackedResultIterator<>(userRole)
				);
		// @formatter:on
		Person result = this.converter.read(Person.class, person);
		assertThat(result).isSameAs(person);
		assertPerson(result);
		verifyQueries();
	}

	private static void assertPerson(Person result) {
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(2L);
		assertThat(result.getFirstName()).isEqualTo("John");
		assertThat(result.getLastName()).isEqualTo("Smith");
		assertThat(result.getManagerId()).isEqualTo(1L);
		Person manager = result.getManager();
		assertThat(manager).isNotNull();
		assertThat(manager).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(manager.getId()).isEqualTo(1L);
		assertThat(manager.getFirstName()).isEqualTo("Alex");
		assertThat(manager.getLastName()).isEqualTo("Jones");
		assertThat(manager.getManagerId()).isNull();
		assertThat(manager.getManager()).isNull();
		List<Account> managerAccounts = manager.getAccounts();
		assertThat(managerAccounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAccounts).hasSize(1);
		assertThat(managerAccounts).element(0).satisfies(account -> {
			assertThat(account.getId()).isEqualTo(1L);
			assertThat(account.getPersonId()).isEqualTo(1L);
			assertThat(account.getNumber()).isEqualTo("1");
			assertThat(account.getType()).isEqualTo("manager");
			List<Role> roles = account.getRoles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(1L);
				assertThat(role.getName()).isEqualTo("admin");
			});
		});
		assertThat(manager.getAddressId()).isEqualTo(1L);
		Address managerAddress = manager.getAddress();
		assertThat(managerAddress).isNotNull();
		assertThat(managerAddress).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddress.getId()).isEqualTo(1L);
		assertThat(managerAddress.getCountryId()).isEqualTo(1L);
		Country managerAddressCountry = managerAddress.getCountry();
		assertThat(managerAddressCountry).isNotNull();
		assertThat(managerAddressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddressCountry.getId()).isEqualTo(1L);
		assertThat(managerAddressCountry.getName()).isEqualTo("USA");
		List<Account> accounts = result.getAccounts();
		assertThat(accounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(accounts).hasSize(2);
		assertThat(accounts).element(0).satisfies(account -> {
			assertThat(account.getId()).isEqualTo(2L);
			assertThat(account.getPersonId()).isEqualTo(2L);
			assertThat(account.getNumber()).isEqualTo("2");
			assertThat(account.getType()).isEqualTo("personal");
			assertThat(account.getRoleIds()).containsExactly(1L, 2L);
			List<Role> roles = account.getRoles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(2);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(1L);
				assertThat(role.getName()).isEqualTo("admin");
			});
			assertThat(roles).element(1).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(2L);
				assertThat(role.getName()).isEqualTo("user");
			});
		});
		assertThat(accounts).element(1).satisfies(account -> {
			assertThat(account.getId()).isEqualTo(3L);
			assertThat(account.getPersonId()).isEqualTo(2L);
			assertThat(account.getNumber()).isEqualTo("3");
			assertThat(account.getType()).isEqualTo("business");
			assertThat(account.getRoleIds()).containsExactly(2L);
			List<Role> roles = account.getRoles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(2L);
				assertThat(role.getName()).isEqualTo("user");
			});
		});
		assertThat(result.getAddressId()).isEqualTo(2L);
		Address address = result.getAddress();
		assertThat(address).isNotNull();
		assertThat(address).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(address.getId()).isEqualTo(2L);
		assertThat(address.getCountryId()).isEqualTo(2L);
		Country addressCountry = address.getCountry();
		assertThat(addressCountry).isNotNull();
		assertThat(addressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(addressCountry.getId()).isEqualTo(2L);
		assertThat(addressCountry.getName()).isEqualTo("Germany");
	}

	private void verifyQueries() {
		InOrder inOrder = inOrder(this.namespaceFactory, this.accountNamespace, this.roleNamespace, this.accountQuery,
				this.roleQuery);
		inOrder.verify(this.namespaceFactory).openNamespace(Account.class);
		inOrder.verify(this.accountQuery).where("person_id", Query.Condition.EQ, 1L);
		inOrder.verify(this.accountQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleQuery).where("id", Query.Condition.EQ, 1L);
		inOrder.verify(this.roleQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Account.class);
		inOrder.verify(this.accountQuery).where("person_id", Query.Condition.EQ, 2L);
		inOrder.verify(this.accountQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleQuery).where("id", Query.Condition.SET, List.of(1L, 2L));
		inOrder.verify(this.roleQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleQuery).where("id", Query.Condition.EQ, 2L);
		inOrder.verify(this.roleQuery).execute();
		inOrder.verifyNoMoreInteractions();
	}

	@SuppressWarnings("unchecked")
	@Test
	void projectRecordDtoUsingQueryFormatVersion2() {
		Role adminRole = createAdminRole();
		Role userRole = createUserRole();
		Person managerPerson = createManagerPerson();
		Person person = createPerson();
		Account managerAccount = createManagerAccount();
		Account personalAccount = createPersonalAccount();
		Account businessAccount = createBusinessAccount();
		EntityProjection<PersonDto, Person> projection = this.converter.getProjectionIntrospector()
			.introspect(PersonDto.class, Person.class);
		this.mappingContext.setQueryFormatVersion(() -> Consts.QUERY_FORMAT_V2);
		when(this.namespaceFactory.openNamespace(Person.class)).thenReturn(this.personNamespace);
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.namespaceFactory.openNamespace(Country.class)).thenReturn(this.countryNamespace);
		when(this.personNamespace.query()).thenReturn(this.personQuery);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		when(this.countryNamespace.query()).thenReturn(this.countryQuery);
		when(this.personQuery.execute()).thenReturn(new ListBackedResultIterator<>(managerPerson));
		// @formatter:off
		when(this.accountQuery.execute())
			.thenReturn(
					new ListBackedResultIterator<>(managerAccount),
					new ListBackedResultIterator<>(personalAccount, businessAccount)
			);
		when(this.roleQuery.execute())
                .thenReturn(
                        new ListBackedResultIterator<>(adminRole),
                        new ListBackedResultIterator<>(adminRole, userRole),
                        new ListBackedResultIterator<>(userRole)
                );
        // @formatter:on
		PersonDto result = this.converter.project(projection, person);
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(2L);
		assertThat(result.getFirstName()).isEqualTo("John");
		assertThat(result.getLastName()).isEqualTo("Smith");
		PersonDto manager = result.getManager();
		assertThat(manager).isInstanceOf(LazyLoadingProxy.class);
		assertThat(manager.getId()).isEqualTo(1L);
		assertThat(manager.getFirstName()).isEqualTo("Alex");
		assertThat(manager.getLastName()).isEqualTo("Jones");
		assertThat(manager.getManager()).isNull();
		List<AccountRecordDto> managerAccounts = manager.getAccounts();
		assertThat(managerAccounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAccounts).hasSize(1);
		assertThat(managerAccounts).element(0).satisfies(account -> {
			assertThat(account.id()).isEqualTo(1L);
			assertThat(account.number()).isEqualTo("1");
			assertThat(account.type()).isEqualTo("manager");
			List<RoleRecordDto> roles = account.roles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.id()).isEqualTo(1L);
				assertThat(role.name()).isEqualTo("admin");
			});
		});
		AddressRecordDto managerAddress = manager.getAddress();
		assertThat(managerAddress).isNotNull();
		assertThat(managerAddress).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddress.id()).isEqualTo(1L);
		CountryRecordDto managerAddressCountry = managerAddress.country();
		assertThat(managerAddressCountry).isNotNull();
		assertThat(managerAddressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddressCountry.id()).isEqualTo(1L);
		assertThat(managerAddressCountry.name()).isEqualTo("USA");
		List<AccountRecordDto> accounts = result.getAccounts();
		assertThat(accounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(accounts).hasSize(2);
		assertThat(accounts).element(0).satisfies(account -> {
			assertThat(account.id()).isEqualTo(2L);
			assertThat(account.number()).isEqualTo("2");
			assertThat(account.type()).isEqualTo("personal");
			List<RoleRecordDto> roles = account.roles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(2);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.id()).isEqualTo(1L);
				assertThat(role.name()).isEqualTo("admin");
			});
			assertThat(roles).element(1).satisfies(role -> {
				assertThat(role.id()).isEqualTo(2L);
				assertThat(role.name()).isEqualTo("user");
			});
		});
		assertThat(accounts).element(1).satisfies(account -> {
			assertThat(account.id()).isEqualTo(3L);
			assertThat(account.number()).isEqualTo("3");
			assertThat(account.type()).isEqualTo("business");
			List<RoleRecordDto> roles = account.roles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.id()).isEqualTo(2L);
				assertThat(role.name()).isEqualTo("user");
			});
		});
		AddressRecordDto address = result.getAddress();
		assertThat(address).isNotNull();
		assertThat(address).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(address.id()).isEqualTo(2L);
		CountryRecordDto country = address.country();
		assertThat(country).isNotNull();
		assertThat(country).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(country.id()).isEqualTo(2L);
		assertThat(country.name()).isEqualTo("Germany");
		verifyQueriesV2();
	}

	@SuppressWarnings("unchecked")
	@Test
	void projectEntityUsingQueryFormatVersion2() {
		Role adminRole = createAdminRole();
		Role userRole = createUserRole();
		Person managerPerson = createManagerPerson();
		Person person = createPerson();
		Account managerAccount = createManagerAccount();
		Account personalAccount = createPersonalAccount();
		Account businessAccount = createBusinessAccount();
		EntityProjection<Person, Person> projection = this.converter.getProjectionIntrospector()
			.introspect(Person.class, Person.class);
		this.mappingContext.setQueryFormatVersion(() -> Consts.QUERY_FORMAT_V2);
		when(this.namespaceFactory.openNamespace(Person.class)).thenReturn(this.personNamespace);
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.namespaceFactory.openNamespace(Country.class)).thenReturn(this.countryNamespace);
		when(this.personNamespace.query()).thenReturn(this.personQuery);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		when(this.countryNamespace.query()).thenReturn(this.countryQuery);
		when(this.personQuery.execute()).thenReturn(new ListBackedResultIterator<>(managerPerson));
		// @formatter:off
		when(this.accountQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(managerAccount),
						new ListBackedResultIterator<>(personalAccount, businessAccount)
				);
		when(this.roleQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(adminRole),
						new ListBackedResultIterator<>(adminRole, userRole),
						new ListBackedResultIterator<>(userRole)
				);
		// @formatter:on
		Person result = this.converter.project(projection, person);
		assertThat(result).isSameAs(person);
		assertPersonV2(result);
		verifyQueriesV2();
	}

	@SuppressWarnings("unchecked")
	@Test
	void readEntityUsingQueryFormatVersion2() {
		Role adminRole = createAdminRole();
		Role userRole = createUserRole();
		Person managerPerson = createManagerPerson();
		Person person = createPerson();
		Account managerAccount = createManagerAccount();
		Account personalAccount = createPersonalAccount();
		Account businessAccount = createBusinessAccount();
		EntityProjection<Person, Person> projection = this.converter.getProjectionIntrospector()
			.introspect(Person.class, Person.class);
		this.mappingContext.setQueryFormatVersion(() -> Consts.QUERY_FORMAT_V2);
		when(this.namespaceFactory.openNamespace(Person.class)).thenReturn(this.personNamespace);
		when(this.namespaceFactory.openNamespace(Account.class)).thenReturn(this.accountNamespace);
		when(this.namespaceFactory.openNamespace(Role.class)).thenReturn(this.roleNamespace);
		when(this.namespaceFactory.openNamespace(Address.class)).thenReturn(this.addressNamespace);
		when(this.namespaceFactory.openNamespace(Country.class)).thenReturn(this.countryNamespace);
		when(this.personNamespace.query()).thenReturn(this.personQuery);
		when(this.accountNamespace.query()).thenReturn(this.accountQuery);
		when(this.roleNamespace.query()).thenReturn(this.roleQuery);
		when(this.addressNamespace.query()).thenReturn(this.addressQuery);
		when(this.countryNamespace.query()).thenReturn(this.countryQuery);
		when(this.personQuery.execute()).thenReturn(new ListBackedResultIterator<>(managerPerson));
		// @formatter:off
		when(this.accountQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(managerAccount),
						new ListBackedResultIterator<>(personalAccount, businessAccount)
				);
		when(this.roleQuery.execute())
				.thenReturn(
						new ListBackedResultIterator<>(adminRole),
						new ListBackedResultIterator<>(adminRole, userRole),
						new ListBackedResultIterator<>(userRole)
				);
		// @formatter:on
		Person result = this.converter.project(projection, person);
		assertThat(result).isSameAs(person);
		assertPersonV2(result);
		verifyQueriesV2();
	}

	private static void assertPersonV2(Person result) {
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(2L);
		assertThat(result.getFirstName()).isEqualTo("John");
		assertThat(result.getLastName()).isEqualTo("Smith");
		assertThat(result.getManagerId()).isEqualTo(1L);
		Person manager = result.getManager();
		assertThat(manager).isInstanceOf(LazyLoadingProxy.class);
		assertThat(manager.getId()).isEqualTo(1L);
		assertThat(manager.getFirstName()).isEqualTo("Alex");
		assertThat(manager.getLastName()).isEqualTo("Jones");
		assertThat(manager.getManagerId()).isNull();
		assertThat(manager.getManager()).isNull();
		List<Account> managerAccounts = manager.getAccounts();
		assertThat(managerAccounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAccounts).hasSize(1);
		assertThat(managerAccounts).element(0).satisfies(account -> {
			assertThat(account.getId()).isEqualTo(1L);
			assertThat(account.getPersonId()).isEqualTo(1L);
			assertThat(account.getNumber()).isEqualTo("1");
			assertThat(account.getType()).isEqualTo("manager");
			assertThat(account.getRoleIds()).containsExactly(1L);
			List<Role> roles = account.getRoles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(1L);
				assertThat(role.getName()).isEqualTo("admin");
			});
		});
		assertThat(manager.getAddressId()).isEqualTo(1L);
		Address managerAddress = manager.getAddress();
		assertThat(managerAddress).isNotNull();
		assertThat(managerAddress).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddress.getId()).isEqualTo(1L);
		assertThat(managerAddress.getCountryId()).isEqualTo(1L);
		Country managerAddressCountry = managerAddress.getCountry();
		assertThat(managerAddressCountry).isNotNull();
		assertThat(managerAddressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(managerAddressCountry.getId()).isEqualTo(1L);
		assertThat(managerAddressCountry.getName()).isEqualTo("USA");
		List<Account> accounts = result.getAccounts();
		assertThat(accounts).isInstanceOf(LazyLoadingProxy.class);
		assertThat(accounts).hasSize(2);
		assertThat(accounts).element(0).satisfies(account -> {
			assertThat(account.getId()).isEqualTo(2L);
			assertThat(account.getPersonId()).isEqualTo(2L);
			assertThat(account.getNumber()).isEqualTo("2");
			assertThat(account.getType()).isEqualTo("personal");
			assertThat(account.getRoleIds()).containsExactly(1L, 2L);
			List<Role> roles = account.getRoles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(2);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(1L);
				assertThat(role.getName()).isEqualTo("admin");
			});
			assertThat(roles).element(1).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(2L);
				assertThat(role.getName()).isEqualTo("user");
			});
		});
		assertThat(accounts).element(1).satisfies(account -> {
			assertThat(account.getId()).isEqualTo(3L);
			assertThat(account.getPersonId()).isEqualTo(2L);
			assertThat(account.getNumber()).isEqualTo("3");
			assertThat(account.getType()).isEqualTo("business");
			assertThat(account.getRoleIds()).containsExactly(2L);
			List<Role> roles = account.getRoles();
			assertThat(roles).isInstanceOf(LazyLoadingProxy.class);
			assertThat(roles).hasSize(1);
			assertThat(roles).element(0).satisfies(role -> {
				assertThat(role.getId()).isEqualTo(2L);
				assertThat(role.getName()).isEqualTo("user");
			});
		});
		assertThat(result.getAddressId()).isEqualTo(2L);
		Address address = result.getAddress();
		assertThat(address).isNotNull();
		assertThat(address).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(address.getId()).isEqualTo(2L);
		assertThat(address.getCountryId()).isEqualTo(2L);
		Country addressCountry = address.getCountry();
		assertThat(addressCountry).isNotNull();
		assertThat(addressCountry).isNotInstanceOf(LazyLoadingProxy.class);
		assertThat(addressCountry.getId()).isEqualTo(2L);
		assertThat(addressCountry.getName()).isEqualTo("Germany");
	}

	private void verifyQueriesV2() {
		InOrder inOrder = inOrder(this.namespaceFactory, this.personNamespace, this.accountNamespace,
				this.roleNamespace, this.addressNamespace, this.countryNamespace, this.personQuery, this.accountQuery,
				this.roleQuery, this.addressQuery, this.countryQuery);
		inOrder.verify(this.namespaceFactory).openNamespace(Person.class);
		inOrder.verify(this.personNamespace).query();
		inOrder.verify(this.namespaceFactory).openNamespace(Address.class);
		inOrder.verify(this.addressNamespace).query();
		inOrder.verify(this.namespaceFactory).openNamespace(Country.class);
		inOrder.verify(this.countryNamespace).query();
		inOrder.verify(this.countryQuery).on("country_id", Query.Condition.EQ, "id");
		inOrder.verify(this.addressQuery).leftJoin(this.countryQuery, "country");
		inOrder.verify(this.addressQuery).on("address_id", Query.Condition.EQ, "id");
		inOrder.verify(this.personQuery).leftJoin(this.addressQuery, "address");
		inOrder.verify(this.personQuery).where("id", Query.Condition.EQ, 1L);
		inOrder.verify(this.namespaceFactory).openNamespace(Account.class);
		inOrder.verify(this.accountNamespace).query();
		inOrder.verify(this.accountQuery).where("person_id", Query.Condition.EQ, 1L);
		inOrder.verify(this.accountQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleQuery).where("id", Query.Condition.EQ, 1L);
		inOrder.verify(this.roleQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Account.class);
		inOrder.verify(this.accountQuery).where("person_id", Query.Condition.EQ, 2L);
		inOrder.verify(this.accountQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleQuery).where("id", Query.Condition.SET, List.of(1L, 2L));
		inOrder.verify(this.roleQuery).execute();
		inOrder.verify(this.namespaceFactory).openNamespace(Role.class);
		inOrder.verify(this.roleQuery).where("id", Query.Condition.EQ, 2L);
		inOrder.verify(this.roleQuery).execute();
		inOrder.verifyNoMoreInteractions();
	}

	@Data
	static class PersonDto {

		Long id;

		String firstName;

		String lastName;

		PersonDto manager;

		List<AccountRecordDto> accounts;

		AddressRecordDto address;

	}

	record AccountRecordDto(Long id, String number, String type, List<RoleRecordDto> roles) {
	}

	record RoleRecordDto(Long id, String name) {
	}

	record AddressRecordDto(Long id, CountryRecordDto country) {
	}

	record CountryRecordDto(Long id, String name) {
	}

	@Data
	@Namespace(name = "persons")
	static class Person {

		@Id
		Long id;

		@Reindex(name = "manager_id")
		Long managerId;

		@Reindex(name = "address_id")
		Long addressId;

		String firstName;

		String lastName;

		@NamespaceReference(indexName = "manager_id")
		Person manager;

		@NamespaceReference(indexName = "id", referencedIndexName = "person_id", lazy = true)
		List<Account> accounts;

		@NamespaceReference(indexName = "address_id")
		Address address;

	}

	@Data
	@Namespace(name = "accounts")
	static class Account {

		@Id
		Long id;

		@Reindex(name = "person_id")
		Long personId;

		@Reindex(name = "role_ids")
		List<Long> roleIds;

		String number;

		String type;

		@NamespaceReference(indexName = "role_ids", lazy = true)
		List<Role> roles;

	}

	@Data
	@Namespace(name = "roles")
	static class Role {

		@Id
		Long id;

		String name;

	}

	@Data
	@Namespace(name = "addresses")
	static class Address {

		@Id
		Long id;

		@Reindex(name = "country_id")
		Long countryId;

		@NamespaceReference(indexName = "country_id", fetch = true)
		Country country;

	}

	@Data
	@Namespace(name = "countries")
	static class Country {

		@Id
		Long id;

		String name;

	}

}
