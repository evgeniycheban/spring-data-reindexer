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
package org.springframework.data.reindexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A utility class for reading repository AOT metadata e.g., query format version used to
 * generate the query for specific methods.
 *
 * @author Evgeniy Cheban
 */
public final class RepositoryAotMetadataUtils {

	private static final Log logger = LogFactory.getLog(RepositoryAotMetadataUtils.class);

	private static final String AOT_METADATA_BASE_DIR = "aot/metadata";

	/**
	 * Reads the query format version used to generate the query for the given method.
	 * @param repositoryClass the repository class to use
	 * @param methodName the method name to use
	 * @return an {@link Optional} containing the query format version or empty if not
	 * found
	 */
	public static Optional<Integer> getQueryFormatVersion(Class<?> repositoryClass, String methodName) {
		Path path = Paths.get(AOT_METADATA_BASE_DIR,
				ClassUtils.convertClassNameToResourcePath(repositoryClass.getName()) + ".json");
		ClassPathResource resource = new ClassPathResource(path.toString());
		if (!resource.exists()) {
			return Optional.empty();
		}
		try (InputStream is = resource.getInputStream()) {
			Object json = JSONValue.parse(is);
			Assert.isInstanceOf(JSONObject.class, json, () -> "Expected JSON object at metadata root: " + path);
			Object methods = ((JSONObject) json).get("methods");
			Assert.isInstanceOf(JSONArray.class, methods, "Expected JSON array at 'methods' key");
			for (Object method : (JSONArray) methods) {
				Assert.isInstanceOf(JSONObject.class, method, "Expected JSON object in 'methods' array");
				JSONObject methodObject = (JSONObject) method;
				if (!methodName.equals(methodObject.get("name"))) {
					continue;
				}
				Object query = methodObject.get("query");
				if (!(query instanceof JSONObject queryObject)) {
					return Optional.empty();
				}
				Number queryFormatVersion = queryObject.getAsNumber("queryFormatVersion");
				if (queryFormatVersion == null) {
					return Optional.empty();
				}
				return Optional.of(queryFormatVersion.intValue());
			}
		}
		catch (IOException ex) {
			logger.error("Failed to parse repository metadata", ex);
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return Optional.empty();
	}

	private RepositoryAotMetadataUtils() {
		// utils
	}

}
