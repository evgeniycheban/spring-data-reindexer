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
package org.springframework.data.reindexer.repository.item;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import ru.rt.restream.reindexer.ResultIterator;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.reindexer.core.mapping.Query;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueJoinedItemProjection;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueProjection;
import org.springframework.data.reindexer.repository.item.dto.TestItemProjection;
import org.springframework.data.reindexer.repository.item.entity.TestItem;
import org.springframework.data.reindexer.repository.item.dto.TestEnum;
import org.springframework.data.reindexer.repository.item.dto.TestItemDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameRecord;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemNameValueRecord;
import org.springframework.data.reindexer.repository.item.dto.TestItemPreferredConstructorDto;
import org.springframework.data.reindexer.repository.item.dto.TestItemPreferredConstructorRecord;
import org.springframework.data.reindexer.repository.item.dto.TestItemProjectionWithJoinedItems;
import org.springframework.data.reindexer.repository.item.dto.TestItemRecord;
import org.springframework.data.reindexer.repository.query.ReindexerResultAccessor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Evgeniy Cheban
 * @author Daniil Cheban
 */
@Repository
public interface TestItemReindexerRepository extends ReindexerRepository<TestItem, Long> {

	Optional<TestItem> findByName(String name);

	Optional<TestItem> findByNameIgnoreCase(String name);

	Optional<TestItem> findByIdAndNameIgnoreCaseAndValueAllIgnoreCase(Long id, String name, String value);

	List<TestItem> findByNameNotIgnoreCase(String name);

	TestItemProjectionWithJoinedItems findProjectionByName(String name);

	Optional<TestItemDto> findTestItemDTOByName(String name);

	Optional<TestItem> findByNameAndValue(String name, String value);

	Optional<TestItem> findByNameOrValue(String name, String value);

	Optional<TestItem> findByNameOrValueNot(String name, String value);

	Optional<TestItem> findByTestEnumString(TestEnum testEnum);

	Optional<TestItem> findByTestEnumOrdinal(TestEnum testEnum);

	ResultIterator<TestItem> findIteratorByName(String name);

	@Query("SELECT * FROM items WHERE name = ?1")
	ResultIterator<TestItem> findIteratorSqlByName(String name);

	@Query(value = "SELECT * FROM items WHERE name = ?1", nativeQuery = true)
	ResultIterator<TestItem> findIteratorNativeSqlByName(String name);

	@Query("SELECT * FROM items WHERE name = :name")
	ResultIterator<TestItem> findIteratorSqlByNameParam(@Param("name") String name);

	@Query(value = "UPDATE items SET name = ?1 WHERE id = ?2", update = true)
	void updateNameSql(String name, Long id);

	@Query(value = "UPDATE items SET name = ?1 WHERE id = ?2", update = true, nativeQuery = true)
	void updateNameNativeSql(String name, Long id);

	@Query(value = "UPDATE items SET name = :name WHERE id = :id", update = true)
	void updateNameSqlParam(@Param("name") String name, @Param("id") Long id);

	@Query(value = "UPDATE items SET defaultDateTime = now(msec) - 3 * 24 * 60 * 60 * 1000 WHERE id = :id",
			update = true)
	void updateDefaultDateTimeMinus3DaysUsingExpression(Long id);

	@Query(value = "DELETE FROM items WHERE name = :name AND value = :value")
	void deleteByNameAndValueSql(String name, String value);

	@Query(value = "DELETE FROM items WHERE name = :name AND value = :value", update = true, nativeQuery = true)
	void deleteByNameAndValueNativeSql(String name, String value);

	TestItem getByName(String name);

	@Query("SELECT COUNT(*) from items WHERE name = ?1")
	long countSqlByName(String name);

	@Query("SELECT SUM(id) from items WHERE name = ?1")
	long sumSqlByName(String name);

	@Query("SELECT MIN(id) from items WHERE name = ?1")
	long minSqlByName(String name);

	@Query("SELECT MAX(id) from items WHERE name = ?1")
	long maxSqlByName(String name);

	@Query("SELECT AVG(id) from items WHERE name = ?1")
	long avgSqlByName(String name);

	@Query("SELECT FACET(id, name ORDER BY id DESC) from items WHERE name = ?1")
	ReindexerResultAccessor<TestItem> facetOrderByIdDescSqlByName(String name);

	@Query("SELECT * FROM items WHERE id > ?1")
	List<TestItem> findOneSqlByIdGreaterThan(Long id);

	@Query("SELECT * FROM items WHERE id >= ?1")
	List<TestItem> findOneSqlByIdGreaterEqualThan(Long id);

	@Query("SELECT * FROM items WHERE id < ?1")
	List<TestItem> findOneSqlByIdLessThan(Long id);

	@Query("SELECT * FROM items WHERE id <= ?1")
	List<TestItem> findOneSqlByIdLessEqualThan(Long id);

	@Query("SELECT * FROM items WHERE name = ?1")
	Optional<TestItem> findOneSqlByName(String name);

	@Query("SELECT * FROM items WHERE nestedItem->name = :name and nestedItem->value = :value")
	Optional<TestItem> findOneSqlByNestedNameAndValue(String name, String value);

	@Query(value = "SELECT * FROM items WHERE name = ?1", nativeQuery = true)
	Optional<TestItem> findOneNativeSqlByName(String name);

	@Query(value = "SELECT * FROM items WHERE name = ':name' AND value = :value", nativeQuery = true)
	Optional<TestItem> findOneNativeSqlByNameQuotedAndValue(String value);

	@Query("SELECT * FROM items WHERE name IS NOT NULL")
	Optional<TestItem> findOneSqlByNameNotNull();

	@Query("SELECT * FROM items WHERE name IS NULL")
	Optional<TestItem> findOneSqlByNameNull();

	@Query("SELECT * FROM items WHERE name = :name and name = value")
	Optional<TestItem> findOneSqlByNameEqValue(String name);

	@Query("SELECT * FROM items WHERE name = :leftName AND (SELECT * FROM items WHERE name = :rightName) IS NOT NULL")
	Optional<TestItem> findAllBySubQueryName(String leftName, String rightName);

	@Query(value = "SELECT * FROM items WHERE name = :leftName MERGE(SELECT * FROM items WHERE name = :rightName)",
			nativeQuery = true)
	List<TestItem> findAllNativeSqlByMergeQueryName(String leftName, String rightName);

	@Query("SELECT * FROM items WHERE name = :leftName UNION ALL(SELECT * FROM items WHERE name = :rightName)")
	List<TestItem> findAllSqlByMergeQueryName(String leftName, String rightName);

	@Query("SELECT * FROM items UNION ALL(SELECT * FROM items WHERE name = :name or name = 'MERGE(SELECT * FROM items WHERE name = :name)')")
	List<TestItem> findAllSqlByMergeQueryNameQuotedParenthesisString(String name);

	@Query("SELECT * FROM items LEFT JOIN test_joined_items joinedItem ON ((joinedItem.id)) = (items.joinedItemId) WHERE name = ?1")
	Optional<TestItem> findOneWithSimpleJoinAliasSqlByName(String name);

	@Query("SELECT * FROM items LEFT JOIN test_joined_items joinedItem ON test_joined_items.id = items.joinedItemId WHERE name = ?1")
	Optional<TestItem> findOneWithSimpleJoinFullNameSqlByName(String name);

	@Query("SELECT * FROM items INNER JOIN test_joined_items joinedItem ON items.joinedItemId = joinedItem.id AND joinedItem.name = ?1 WHERE name = ?1")
	Optional<TestItem> findOneWithConditionalJoinSqlByName(String name);

	@Query("SELECT * FROM items INNER JOIN test_joined_items joinedItem ON joinedItem.id = items.joinedItemId ON items.value = test_joined_items.value WHERE name = ?1")
	Optional<TestItem> findOneWithMultipleOnJoinSqlByName(String name);

	@Query("SELECT * FROM items it INNER JOIN test_joined_items joinedItems ON joinedItems.id IN it.joinedItemIds WHERE it.name = ?1")
	Optional<TestItem> findOneWithSimpleJoinInSqlByName(String name);

	@Query("SELECT * FROM items it INNER JOIN test_joined_items joinedItems ON (joinedItems.id IN it.joinedItemIds) AND joinedItems.name = it.name WHERE it.name = ?1")
	Optional<TestItem> findOneWithConditionalJoinInSqlByName(String name);

	@Query("SELECT * FROM items WHERE name = :name")
	Optional<TestItem> findOneSqlByNameParam(@Param("name") String name);

	@Query("SELECT * FROM items WHERE name = ?11 AND value = ?12")
	Optional<TestItem> findOneSqlByNameAndValueManyParams(@Nullable String name1, @Nullable String name2,
			@Nullable String name3, @Nullable String name4, @Nullable String name5, @Nullable String name6,
			@Nullable String name7, @Nullable String name8, @Nullable String name9, @Nullable String name10,
			String name11, String value);

	@Query("SELECT * FROM items WHERE id = ?1 AND name = ?2 AND value = ?3")
	Optional<TestItem> findOneSqlByIdAndNameAndValue(Long id, String name, String value);

	@Query("SELECT * FROM items WHERE id = :id AND name = :name AND value = :value")
	Optional<TestItem> findOneSqlByIdAndNameAndValueParam(@Param("id") Long id, @Param("name") String name,
			@Param("value") String value);

	@Query("SELECT * FROM items WHERE id = ?#{[0]} AND name = ?#{[1]} AND value = ?#{[2]}")
	Optional<TestItem> findOneSqlSpelByIdAndNameAndValueParam(Long id, String name, String value);

	@Query("SELECT * FROM items WHERE id = :#{#item.id} AND name = :#{#item.name} AND value = :#{#item.value}")
	Optional<TestItem> findOneSqlSpelByItemIdAndNameAndValueParam(TestItem item);

	@Query(value = "SELECT * FROM items WHERE id = :id AND name = :name AND value = :value", nativeQuery = true)
	Optional<TestItem> findOneNativeSqlByIdAndNameAndValueParam(@Param("id") Long id, @Param("name") String name,
			@Param("value") String value);

	@Query(value = "SELECT * FROM items WHERE id = ?#{[0]} AND name = ?#{[1]} AND value = ?#{[2]}", nativeQuery = true)
	Optional<TestItem> findOneNativeSqlSpelByIdAndNameAndValueParam(Long id, String name, String value);

	@Query(value = "SELECT * FROM items WHERE id = :#{#item.id} AND name = :#{#item.name} AND value = :#{#item.value}",
			nativeQuery = true)
	Optional<TestItem> findOneNativeSqlSpelByItemIdAndNameAndValueParam(TestItem item);

	@Query("SELECT * FROM items WHERE id = ?2 AND name = ?3 AND value = ?1")
	Optional<TestItem> findOneSqlByIdAndNameAndValue(String value, Long id, String name);

	@Query("SELECT * FROM items WHERE id = :id AND name = :name AND value = :value")
	Optional<TestItem> findOneSqlByIdAndNameAndValueParam(@Param("value") String value, @Param("id") Long id,
			@Param("name") String name);

	@Query("SELECT * FROM items WHERE name = ?1")
	TestItem getOneSqlByName(String name);

	@Query(value = "SELECT * FROM items WHERE name = ?1", nativeQuery = true)
	TestItem getOneNativeSqlByName(String name);

	@Query("SELECT * FROM items WHERE name = :name")
	TestItem getOneSqlByNameParam(@Param("name") String name);

	@Query("SELECT * FROM items")
	List<TestItem> findAllListSql();

	@Query("SELECT * FROM items")
	Set<TestItem> findAllSetSql();

	@Query("SELECT * FROM items")
	Stream<TestItem> findAllStreamSql();

	List<TestItem> findByIdIn(List<Long> ids);

	List<TestItem> findByIdIn(long... ids);

	List<TestItem> findByIdNotIn(List<Long> ids);

	List<TestItem> findByTestEnumStringIn(List<TestEnum> values);

	List<TestItem> findByTestEnumStringIn(TestEnum... values);

	List<TestItem> findByTestEnumOrdinalIn(List<TestEnum> values);

	List<TestItem> findByTestEnumOrdinalIn(TestEnum... values);

	boolean existsByName(String name);

	long countByValue(String value);

	long countByIdIn(List<Long> ids);

	void deleteByName(String name);

	List<TestItem> findAllByIdIn(List<Long> ids, Sort sort);

	List<TestItem> findByIdIn(List<Long> ids, Pageable pageable);

	Page<TestItem> findPageByIdIn(List<Long> ids, Pageable pageable);

	Page<TestItem> findFirst2By(Pageable pageable);

	Page<TestItem> findFirst3By(Pageable pageable);

	List<TestItemProjection> findItemProjectionByIdIn(List<Long> ids);

	List<TestItemDto> findItemDtoByIdIn(List<Long> ids);

	List<TestItemPreferredConstructorDto> findItemPreferredConstructorDtoByIdIn(List<Long> ids);

	List<TestItemRecord> findItemRecordByIdIn(List<Long> ids);

	List<TestItemPreferredConstructorRecord> findItemPreferredConstructorRecordByIdIn(List<Long> ids);

	<T> List<T> findByIdIn(List<Long> ids, Class<T> type);

	<T> Optional<T> findById(Long id, Class<T> type);

	List<TestItem> findAllBy(Limit limit);

	Optional<TestItem> findFirstByOrderByIdAsc();

	Optional<TestItem> findFirstByOrderByIdDesc();

	Optional<TestItem> findTopByOrderByIdAsc();

	Optional<TestItem> findTopByOrderByIdDesc();

	List<TestItem> findTop10ByOrderByIdAsc();

	List<TestItem> findTop10ByOrderByIdDesc();

	List<TestItemNameRecord> findDistinctNameRecordByIdIn(List<Long> ids);

	List<TestItemNameValueRecord> findDistinctNameValueRecordByIdIn(List<Long> ids);

	List<TestItemNameValueProjection> findDistinctNameValueProjectionByIdIn(List<Long> ids);

	List<TestItemNameValueJoinedItemProjection> findDistinctNameValueJoinedItemProjectionByIdIn(List<Long> ids);

	List<TestItemNameValueDto> findDistinctNameValueDtoByIdIn(List<Long> ids);

	<T> List<T> findDistinctByIdIn(List<Long> ids, Class<T> type);

	Stream<TestItemNameValueRecord> streamDistinctNameValueRecordByIdIn(List<Long> ids);

	List<TestItem> findAllByIdBetween(Long start, Long end);

	List<TestItem> findByActiveIsTrue();

	List<TestItem> findByActiveIsFalse();

	@Query("SELECT id, name FROM items WHERE id IN :ids")
	List<TestItemProjection> findAllItemProjectionByIdIn(List<Long> ids, Sort sort);

	@Query("SELECT id, name FROM items WHERE id IN :ids ORDER BY id DESC")
	List<TestItemDto> findAllItemDtoByIdIn(List<Long> ids, Sort sort);

	@Query("SELECT id, name FROM items WHERE id IN :ids")
	List<TestItemRecord> findAllItemRecordByIdIn(List<Long> ids);

	@Query("SELECT id, name FROM items WHERE NOT id IN :ids")
	List<TestItemRecord> findAllItemRecordByIdNotIn(List<Long> ids);

	@Query("SELECT *, COUNT(*) FROM items WHERE id IN :ids")
	Page<TestItem> findAllCountByIdIn(List<Long> ids, Pageable pageable);

	@Query(value = """
			SELECT *, COUNT(*) FROM items WHERE id IN :#{#ids}
			ORDER BY 'id' :#{#pageable.getSort().getOrderFor('id').getDirection()},
			         'name' :#{#pageable.getSort().getOrderFor('name').getDirection()}
			LIMIT :#{#pageable.getPageSize()} OFFSET :#{#pageable.getOffset()}""", nativeQuery = true)
	Page<TestItem> findPageByIdInNativeSql(List<Long> ids, Pageable pageable);

	@Query("SELECT *, COUNT_CACHED(*) FROM items WHERE id IN :ids")
	Page<TestItem> findAllCountCachedByIdIn(List<Long> ids, Pageable pageable);

	@Query("SELECT *, COUNT_CACHED(*) FROM items LIMIT 2")
	Page<TestItem> findFirst2Sql(Pageable pageable);

	@Query("SELECT *, COUNT_CACHED(*) FROM items LIMIT 3")
	Page<TestItem> findFirst3Sql(Pageable pageable);

	@Query("SELECT * FROM items")
	List<TestItem> findAllSqlLimit(Limit limit);

	List<TestItem> findAllByNameLike(String pattern);

	List<TestItem> findAllByNameNotLike(String pattern);

	List<TestItem> findAllByCitiesContaining(String city);

	List<TestItem> findAllByCitiesNotContaining(String city);

	List<TestItem> findAllByNameContaining(String text);

	List<TestItem> findAllByNameNotContaining(String text);

	List<TestItem> findAllByNameStartingWith(String text);

	List<TestItem> findAllByNameEndingWith(String text);

	List<TestItem> findAllByDefaultDateBetween(LocalDate start, LocalDate end);

	@Query(value = "SELECT * FROM items WHERE defaultDate RANGE (:start, :end)", nativeQuery = true)
	List<TestItem> findAllByDefaultDateBetweenNativeSql(long start, long end);

	@Query("SELECT * FROM items WHERE RANGE (defaultDate, :start, :end)")
	List<TestItem> findAllByDefaultDateBetweenSql(LocalDate start, LocalDate end);

	Optional<TestItem> findByDefaultTime(LocalTime time);

	Optional<TestItem> findByDefaultDate(LocalDate date);

	Optional<TestItem> findByDefaultDateTime(LocalDateTime dateTime);

	@Query("SELECT * FROM items WHERE defaultTime = :time")
	Optional<TestItem> findByDefaultTimeSql(LocalTime time);

	@Query("SELECT * FROM items WHERE defaultDate = :date")
	Optional<TestItem> findByDefaultDateSql(LocalDate date);

	@Query("SELECT * FROM items WHERE defaultDateTime = :dateTime")
	Optional<TestItem> findByDefaultDateTimeSql(LocalDateTime dateTime);

	Optional<TestItem> findByCustomTime(LocalTime time);

	Optional<TestItem> findByCustomDate(LocalDate date);

	Optional<TestItem> findByCustomDateTime(LocalDateTime dateTime);

	@Query("SELECT * FROM items WHERE customTime = :time")
	Optional<TestItem> findByCustomTimeSql(LocalTime time);

	@Query("SELECT * FROM items WHERE customDate = :date")
	Optional<TestItem> findByCustomDateSql(LocalDate date);

	@Query("SELECT * FROM items WHERE customDateTime = :dateTime")
	Optional<TestItem> findByCustomDateTimeSql(LocalDateTime dateTime);

	@Query("SELECT * FROM items WHERE defaultDateTime <= now(msec)")
	List<TestItem> findPastItems();

	@Query("SELECT * FROM items WHERE now(msec) >= defaultDateTime")
	List<TestItem> findPastItemsInverted();

	@Query("SELECT * FROM items WHERE flat_array_len(cities) > :citiesLength")
	List<TestItem> findItemsCitiesLengthGreaterThan(int citiesLength);

	@Query("SELECT * FROM items WHERE :citiesLength < flat_array_len(cities)")
	List<TestItem> findItemsCitiesLengthGreaterThanInverted(int citiesLength);

	Slice<TestItem> findAllBy(Pageable pageable);

	Slice<TestItem> findAllByIdIn(List<Long> ids, Pageable pageable);

	@Query("SELECT * FROM items")
	Slice<TestItem> findAllSliceSql(Pageable pageable);

	@Query("SELECT * FROM items WHERE id IN :ids")
	Slice<TestItem> findAllByIdInSliceSql(List<Long> ids, Pageable pageable);

	@Query(value = "SELECT * FROM items WHERE id IN :#{#ids} LIMIT :#{#pageable.getPageSize() + 1} OFFSET :#{#pageable.getOffset()}",
			nativeQuery = true)
	Slice<TestItem> findAllByIdInSliceNativeSql(List<Long> ids, Pageable pageable);

}
