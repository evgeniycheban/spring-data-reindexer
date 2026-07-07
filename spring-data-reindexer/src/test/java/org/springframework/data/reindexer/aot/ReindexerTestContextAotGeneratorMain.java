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
package org.springframework.data.reindexer.aot;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.generate.FileSystemGeneratedFiles;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.test.context.aot.TestContextAotGenerator;
import org.springframework.util.Assert;

/**
 * An executable utility that runs a {@link TestContextAotGenerator} to generate AOT
 * artifacts for integration tests that depend on support from the <em>Spring TestContext
 * Framework</em>.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerTestContextAotGeneratorMain {

	private static final Log LOG = LogFactory.getLog(ReindexerTestContextAotGeneratorMain.class);

	public static void main(String[] args) {
		Assert.isTrue(args.length >= 2, () -> "Usage: %s <sourceOutput> <classpathRoots>");
		Path sourceOutput = Paths.get(args[0]);
		String classPathRoots = args[1];
		LOG.info(String.format("Generating AOT artifacts for: %s to: %s", classPathRoots, sourceOutput));
		GeneratedFiles generatedFiles = new FileSystemGeneratedFiles(sourceOutput);
		TestContextAotGenerator generator = new TestContextAotGenerator(generatedFiles);
		Stream<Class<?>> testClasses = Stream.of(classPathRoots.split(File.pathSeparator))
			.map(ReindexerTestContextAotGeneratorMain::loadClass);
		generator.processAheadOfTime(testClasses);
	}

	private static Class<?> loadClass(String className) {
		try {
			return Class.forName(className);
		}
		catch (ClassNotFoundException e) {
			throw new RuntimeException("Test class: '" + className + "' not found", e);
		}
	}

}
