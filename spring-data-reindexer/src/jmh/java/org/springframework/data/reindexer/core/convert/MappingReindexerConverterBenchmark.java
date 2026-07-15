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
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.platform.commons.annotation.Testable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import ru.rt.restream.reindexer.Reindexer;
import ru.rt.restream.reindexer.ReindexerConfiguration;

import org.springframework.data.annotation.Id;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.reindexer.AbstractMicrobenchmark;
import org.springframework.data.reindexer.core.mapping.Namespace;
import org.springframework.data.reindexer.core.mapping.ReindexerMappingContext;
import org.springframework.data.reindexer.repository.support.DefaultReindexerNamespaceFactory;

/**
 * @author Evgeniy Cheban
 */
@State(Scope.Benchmark)
@Testable
public class MappingReindexerConverterBenchmark extends AbstractMicrobenchmark {

	private static final int RPC_PORT = 6534;

	private static final String DB_NAME = "test";

	private Reindexer reindexer;

	private MappingReindexerConverter converter;

	private EntityProjection<CustomerRecord, Customer> customerRecordProjection;

	private EntityProjection<CustomerWithAddressRecord, Customer> customerWithAddressRecordProjection;

	private EntityProjection<CustomerWithAddressAndBankAccountsRecord, Customer> customerWithAddressAndBankAccountsRecordProjection;

	private Customer customer;

	@Setup
	public void setup() {
		// @formatter:off
		this.customer = new Customer(UUID.randomUUID(), "John", "Doe", new Address("1111", "New York"), List.of(
				new BankAccount(new BankAccountInformation("123", "PERSONAL"), new Address("2222", "New York")),
				new BankAccount(new BankAccountInformation("456", "BUSINESS"), new Address("3333", "Boston")),
				new BankAccount(new BankAccountInformation("789", "SAVINGS"), new Address("4444", "Washington"))
		));
		// @formatter:on
		this.reindexer = ReindexerConfiguration.builder()
			.url("cproto://localhost:" + RPC_PORT + "/" + DB_NAME)
			.getReindexer();
		ReindexerMappingContext mappingContext = new ReindexerMappingContext();
		mappingContext.setInitialEntitySet(Set.of(Customer.class));
		mappingContext.setSimpleTypeHolder(ReindexerSimpleTypes.HOLDER);
		this.converter = new MappingReindexerConverter(this.reindexer, mappingContext,
				new DefaultReindexerNamespaceFactory(this.reindexer, mappingContext));
		this.customerRecordProjection = this.converter.getProjectionIntrospector()
			.introspect(CustomerRecord.class, Customer.class);
		this.customerWithAddressRecordProjection = this.converter.getProjectionIntrospector()
			.introspect(CustomerWithAddressRecord.class, Customer.class);
		this.customerWithAddressAndBankAccountsRecordProjection = this.converter.getProjectionIntrospector()
			.introspect(CustomerWithAddressAndBankAccountsRecord.class, Customer.class);
	}

	@TearDown
	public void tearDown() {
		this.reindexer.close();
	}

	@Benchmark
	public CustomerRecord projectCustomerRecord() {
		return this.converter.project(this.customerRecordProjection, this.customer);
	}

	@Benchmark
	public CustomerWithAddressRecord projectCustomerWithAddressRecord() {
		return this.converter.project(this.customerWithAddressRecordProjection, this.customer);
	}

	@Benchmark
	public CustomerWithAddressAndBankAccountsRecord projectCustomerWithAddressAndBankAccountsRecord() {
		return this.converter.project(this.customerWithAddressAndBankAccountsRecordProjection, this.customer);
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Namespace(name = "customers")
	public static class Customer {

		@Id
		private UUID id;

		private String firstName;

		private String lastName;

		private Address address;

		private List<BankAccount> bankAccounts;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Address {

		private String zipCode;

		private String city;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BankAccount {

		private BankAccountInformation information;

		private Address address;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BankAccountInformation {

		private String accountNumber;

		private String accountType;

	}

	public record CustomerRecord(String id, String firstName, String lastName) {
	}

	public record CustomerWithAddressRecord(String id, String firstName, String lastName, AddressRecord address) {
	}

	public record CustomerWithAddressAndBankAccountsRecord(String id, String firstName, String lastName,
			AddressRecord address, List<BankAccountRecord> bankAccounts) {
	}

	public record BankAccountRecord(BankAccountInformationRecord information, AddressRecord address) {
	}

	public record BankAccountInformationRecord(String accountNumber, String accountType) {
	}

	public record AddressRecord(String zipCode, String city) {
	}

}
