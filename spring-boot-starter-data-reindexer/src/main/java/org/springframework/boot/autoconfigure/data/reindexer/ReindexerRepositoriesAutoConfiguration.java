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
package org.springframework.boot.autoconfigure.data.reindexer;

import ru.rt.restream.reindexer.Reindexer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.reindexer.repository.ReindexerRepository;
import org.springframework.data.reindexer.repository.config.EnableReindexerRepositories;
import org.springframework.data.reindexer.repository.support.ReindexerRepositoryFactoryBean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Data's Reindexer
 * Repositories.
 * <p>
 * Activates when there is no bean of type
 * {@link org.springframework.data.reindexer.repository.support.ReindexerRepositoryFactoryBean}
 * configured in the context, the Spring Data Reindexer
 * {@link org.springframework.data.reindexer.repository.ReindexerRepository} type is on
 * the classpath, the Reindexer client driver API is on the classpath, and there is no
 * other configured
 * {@link org.springframework.data.reindexer.repository.ReindexerRepository}.
 * <p>
 * Once in effect, the autoconfiguration is the equivalent of enabling Reindexer
 * repositories using the {@link EnableReindexerRepositories @EnableReindexerRepositories}
 * annotation.
 *
 * @author Evgeniy Cheban
 * @since 1.0
 */
@AutoConfiguration(after = ReindexerDataAutoConfiguration.class)
@ConditionalOnClass({ Reindexer.class, ReindexerRepository.class })
@ConditionalOnBooleanProperty(name = "spring.data.reindexer.repositories.enabled", matchIfMissing = true)
@ConditionalOnMissingBean(ReindexerRepositoryFactoryBean.class)
@Import(ReindexerRepositoriesRegistrar.class)
public class ReindexerRepositoriesAutoConfiguration {

}
