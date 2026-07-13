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
package org.springframework.boot.autoconfigure.data.reindexer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import ru.rt.restream.reindexer.binding.cproto.DataSourceFactory;
import ru.rt.restream.reindexer.binding.cproto.DataSourceFactoryStrategy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Reindexer.
 *
 * @author Evgeniy Cheban
 * @since 1.0
 */
@ConfigurationProperties("spring.data.reindexer")
public class ReindexerProperties {

	/**
	 * Reindexer database urls.
	 */
	private List<String> urls = new ArrayList<>(Collections.singletonList("cproto://localhost:6534/test"));

	/**
	 * Allows usage of the database urls from #replicationstats which are not in the list
	 * of urls.
	 */
	private boolean allowUnlistedDataSource = false;

	/**
	 * Enables automatic creation of missing indexes. Defaults to {@literal false}.
	 */
	private boolean autoIndexCreation = false;

	/**
	 * Configure a {@link DataSourceFactory}. Defaults to
	 * {@link DataSourceFactoryStrategy#NEXT}.
	 */
	private DataSourceFactory dataSourceFactory = DataSourceFactoryStrategy.NEXT;

	/**
	 * Configure reindexer connection pool size. Defaults to 8.
	 */
	private int connectionPoolSize = 8;

	/**
	 * Configure reindexer request timeout. Defaults to 60 seconds.
	 */
	private Duration requestTimeout = Duration.ofSeconds(60L);

	/**
	 * Configure reindexer server startup timeout. Defaults to 3 minutes.
	 */
	private Duration serverStartupTimeout = Duration.ofMinutes(3L);

	/**
	 * Configure reindexer server config file. Defaults to
	 * "default-builtin-server-config.yml".
	 */
	private String serverConfigFile = "default-builtin-server-config.yml";

	/**
	 * Configure reindexer server SSL connection when cprotos protocol is used.
	 */
	private @Nullable Ssl ssl;

	public List<String> getUrls() {
		return this.urls;
	}

	public void setUrls(List<String> urls) {
		this.urls = urls;
	}

	public boolean isAllowUnlistedDataSource() {
		return this.allowUnlistedDataSource;
	}

	public void setAllowUnlistedDataSource(boolean allowUnlistedDataSource) {
		this.allowUnlistedDataSource = allowUnlistedDataSource;
	}

	public boolean isAutoIndexCreation() {
		return this.autoIndexCreation;
	}

	public void setAutoIndexCreation(boolean autoIndexCreation) {
		this.autoIndexCreation = autoIndexCreation;
	}

	public DataSourceFactory getDataSourceFactory() {
		return this.dataSourceFactory;
	}

	public void setDataSourceFactory(DataSourceFactory dataSourceFactory) {
		this.dataSourceFactory = dataSourceFactory;
	}

	public int getConnectionPoolSize() {
		return this.connectionPoolSize;
	}

	public void setConnectionPoolSize(int connectionPoolSize) {
		this.connectionPoolSize = connectionPoolSize;
	}

	public Duration getRequestTimeout() {
		return this.requestTimeout;
	}

	public void setRequestTimeout(Duration requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Duration getServerStartupTimeout() {
		return this.serverStartupTimeout;
	}

	public void setServerStartupTimeout(Duration serverStartupTimeout) {
		this.serverStartupTimeout = serverStartupTimeout;
	}

	public String getServerConfigFile() {
		return this.serverConfigFile;
	}

	public void setServerConfigFile(String serverConfigFile) {
		this.serverConfigFile = serverConfigFile;
	}

	public @Nullable Ssl getSsl() {
		return this.ssl;
	}

	public void setSsl(Ssl ssl) {
		this.ssl = ssl;
	}

	public static class Ssl {

		/**
		 * Enable SSL support.
		 */
		private boolean enabled = true;

		/**
		 * Configure KeyStore.
		 */
		private @Nullable String keyStore;

		/**
		 * Configure KeyStore password.
		 */
		private @Nullable String keyStorePassword;

		/**
		 * Configure KeyStore type. Defaults to JKS.
		 */
		private String keyStoreType = "JKS";

		/**
		 * Configure secured connection protocol. Defaults to TLS.
		 */
		private String protocol = "TLS";

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public @Nullable String getKeyStore() {
			return this.keyStore;
		}

		public void setKeyStore(String keyStore) {
			this.keyStore = keyStore;
		}

		public @Nullable String getKeyStorePassword() {
			return this.keyStorePassword;
		}

		public void setKeyStorePassword(String keyStorePassword) {
			this.keyStorePassword = keyStorePassword;
		}

		public String getKeyStoreType() {
			return this.keyStoreType;
		}

		public void setKeyStoreType(String keyStoreType) {
			this.keyStoreType = keyStoreType;
		}

		public String getProtocol() {
			return this.protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}

	}

}
