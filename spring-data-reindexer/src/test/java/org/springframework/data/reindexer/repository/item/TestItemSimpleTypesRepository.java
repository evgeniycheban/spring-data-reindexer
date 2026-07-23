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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.item.entity.TestItemSimpleTypes;
import org.springframework.stereotype.Repository;

/**
 * @author Evgeniy Cheban
 */
@Repository
public interface TestItemSimpleTypesRepository extends ReindexerRepository<TestItemSimpleTypes, BigInteger> {

	List<TestItemSimpleTypes> findAllByBigIntegerGreaterThan(BigInteger bigInteger);

	List<TestItemSimpleTypes> findAllByBigDecimalGreaterThan(BigDecimal bigDecimal);

}
