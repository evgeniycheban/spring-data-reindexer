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
package org.springframework.boot.autoconfigure;

import java.io.IOException;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.util.Assert;
import org.testcontainers.utility.MountableFile;

/**
 * Testcontainers implementation for Reindexer.
 * <p>
 * Supported image: {@code reindexer/reindexer}
 * </p>
 * Exposed ports:
 * <ul>
 * <li>HTTP: 9088</li>
 * <li>RPC: 6534</li>
 * <li>RPC-TLS: 6535 (if SSL is enabled)</li>
 * </ul>
 *
 * @author Evgeniy Cheban
 */
public class ReindexerContainer extends GenericContainer<ReindexerContainer> {

	private static final int HTTP_PORT = 9088;

	private static final int RPC_PORT = 6534;

	private static final int RPC_SSL_PORT = 6535;

	private static final DockerImageName REINDEXER_IMAGE = DockerImageName.parse("reindexer/reindexer");

	private final String database;

	/**
	 * Creates an instance.
	 * @param database the database name to use to create a database after container is
	 * started.
	 */
	public ReindexerContainer(String database) {
		super(REINDEXER_IMAGE);
		Assert.hasText(database, "database must not be empty");
		this.database = database;
		addExposedPorts(HTTP_PORT, RPC_PORT);
	}

	/**
	 * Enables SSL support for Reindexer instance using provided {@literal cert} and
	 * {@literal key}.
	 * @param cert the SSL certificate to use
	 * @param key the SSL key to use
	 * @return the {@link ReindexerContainer} for further customizations
	 */
	public ReindexerContainer withSsl(String cert, String key) {
		addExposedPorts(RPC_SSL_PORT);
		return withCopyFileToContainer(MountableFile.forClasspathResource(cert), "/" + cert)
			.withCopyFileToContainer(MountableFile.forClasspathResource(key), "/" + key)
			.withEnv("RX_SSL_CERT", "/" + cert)
			.withEnv("RX_SSL_KEY", "/" + key);
	}

	/**
	 * Returns a URL to connect to Reindexer container using RPC protocol.
	 * @return the URL to connect to Reindexer container using RPC protocol
	 */
	public String getRpcUrl() {
		return "cproto://" + getHost() + ":" + getMappedPort(RPC_PORT) + "/" + this.database;
	}

	/**
	 * Returns a URL to connect to Reindexer container using secured (SSL/TLS) RPC
	 * protocol connection.
	 * @return the URL to connect to Reindexer container using secured (SSL/TLS) RPC
	 * protocol connection
	 */
	public String getRpcSslUrl() {
		return "cprotos://" + getHost() + ":" + getMappedPort(RPC_SSL_PORT) + "/" + this.database;
	}

	@Override
	protected void containerIsStarted(InspectContainerResponse containerInfo) {
		createDatabase(this.database);
	}

	private void createDatabase(String database) {
		try {
			request(HttpPost.METHOD_NAME, "/db", new CreateDatabase(database));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void request(String method, String path, Object body) throws IOException {
		String url = "http://" + getHost() + ":" + getMappedPort(HTTP_PORT) + "/api/v1" + path;
		Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
		String json = gson.toJson(body);
		ClassicHttpRequest request = ClassicRequestBuilder.create(method)
			.setUri(url)
			.setEntity(new StringEntity(json))
			.build();
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			client.execute(request);
		}
	}

	private record CreateDatabase(String name) {
	}

}
