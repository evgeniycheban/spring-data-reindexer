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
package org.springframework.data.reindexer.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.reindexer.repository.config.EnableReindexerAuditing;
import org.springframework.data.reindexer.repository.item.TestAuditableItemRepository;
import org.springframework.data.reindexer.repository.item.entity.TestAuditableItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * Tests for {@link ReindexerRepository}'s auditing support using
 * {@link EnableReindexerAuditing} annotation.
 *
 * @author Evgeniy Cheban
 */
class ReindexerAuditingRepositoryTests extends AbstractReindexerTest {

	@Autowired
	TestAuditableItemRepository repository;

	@Autowired
	DateTimeProvider dateTimeProvider;

	@Autowired
	AuditorAware<?> auditorAware;

	@Test
	void saveWhenEntityIsNewThenPopulatesCreatedDateAndLastModifiedDate() {
		LocalDateTime now = LocalDateTime.now();
		doReturn(Optional.of(now)).when(this.dateTimeProvider).getNow();
		TestAuditableItem saved = this.repository.save(TestAuditableItem.builder().build());
		assertThat(saved).isNotNull();
		assertThat(saved.getCreatedAt()).isEqualTo(now);
		assertThat(saved.getModifiedAt()).isEqualTo(now);
	}

	@Test
	void saveWhenEntityIsNotNewThenPopulatesLastModifiedDate() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime yesterday = now.minusDays(1L);
		doReturn(Optional.of(now)).when(this.dateTimeProvider).getNow();
		TestAuditableItem saved = this.repository
			.save(TestAuditableItem.builder().id(1L).createdAt(yesterday).modifiedAt(yesterday).build());
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo(1L);
		assertThat(saved.getCreatedAt()).isEqualTo(yesterday);
		assertThat(saved.getModifiedAt()).isEqualTo(now);
	}

	@Test
	void saveWhenEntityIsNewThenPopulatesCreatedByAndLastModifiedBy() {
		String reindexerUser = "reindexer-user";
		doReturn(Optional.of(reindexerUser)).when(this.auditorAware).getCurrentAuditor();
		TestAuditableItem saved = this.repository.save(TestAuditableItem.builder().build());
		assertThat(saved).isNotNull();
		assertThat(saved.getCreatedBy()).isEqualTo(reindexerUser);
		assertThat(saved.getModifiedBy()).isEqualTo(reindexerUser);
	}

	@Test
	void saveWhenEntityIsNotNewThenPopulatesLastModifiedBy() {
		String reindexerUser = "reindexer-user";
		String reindexerAdmin = "reindexer-admin";
		doReturn(Optional.of(reindexerAdmin)).when(this.auditorAware).getCurrentAuditor();
		TestAuditableItem saved = this.repository
			.save(TestAuditableItem.builder().id(1L).createdBy(reindexerUser).modifiedBy(reindexerUser).build());
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo(1L);
		assertThat(saved.getCreatedBy()).isEqualTo(reindexerUser);
		assertThat(saved.getModifiedBy()).isEqualTo(reindexerAdmin);
	}

}
