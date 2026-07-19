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
package org.springframework.data.reindexer.container;

import java.io.IOException;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Bootstraps Reindexer test container.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerTestContainer {

	private static final int REST_API_PORT = 9088;

	private static final int RPC_PORT = 6534;

	private static final int PROXY_RPC_PORT = 8666;

	private static final String DATABASE_NAME = "test";

	private static final Network REINDEXER_NETWORK = Network.newNetwork();

	private static final GenericContainer<?> REINDEXER = new GenericContainer<>(
			DockerImageName.parse("reindexer/reindexer"))
		.withNetwork(REINDEXER_NETWORK)
		.withNetworkAliases("reindexer")
		.withExposedPorts(REST_API_PORT, RPC_PORT);

	private static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy")
		.withNetwork(REINDEXER_NETWORK);

	private final static Proxy PROXY;

	static {
		// Start containers in parallel.
		Startables.deepStart(REINDEXER, TOXIPROXY).join();
		ToxiproxyClient toxiproxyClient = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
		try {
			PROXY = toxiproxyClient.createProxy("reindexer", "0.0.0.0:" + PROXY_RPC_PORT, "reindexer:" + RPC_PORT);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		request(HttpPost.METHOD_NAME, "/db", new CreateDatabase(DATABASE_NAME));
	}

	/**
	 * Disables the Reindexer proxy to simulate a server outage.
	 */
	public static void disable() {
		try {
			PROXY.disable();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Enables the Reindexer proxy to continue serving requests.
	 */
	public static void enable() {
		try {
			PROXY.enable();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Returns the cproto URL of the Reindexer server.
	 * @return the cproto URL of the Reindexer server to use
	 */
	public static String getCprotoUrl() {
		return "cproto://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(PROXY_RPC_PORT) + "/" + DATABASE_NAME;
	}

	private static void request(String method, String path, Object body) {
		String url = "http://" + REINDEXER.getHost() + ":" + REINDEXER.getMappedPort(REST_API_PORT) + "/api/v1" + path;
		Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
		String json = gson.toJson(body);
		ClassicHttpRequest request = ClassicRequestBuilder.create(method)
			.setUri(url)
			.setEntity(new StringEntity(json))
			.build();
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			client.execute(request);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private record CreateDatabase(String name) {
	}

}
