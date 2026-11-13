/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bson.json.JsonMode;

/**
 * The golden-file <strong>generator</strong> for the {@link EnforcedThingMapper} pre-refactor baseline
 * (Task A1). This is a plain {@code main} entry point - deliberately <em>not</em> a JUnit {@code @Test}, so
 * no test runner (Surefire's filename-glob include/exclude, an IDE's annotation-scanning "Run all tests",
 * or anything else that discovers tests by inspecting bytecode/annotations) can ever pick it up and silently
 * overwrite the committed expected files under {@code src/test/resources/golden/expected/} as a side effect
 * of running the test suite. It must only ever run via the single explicit command documented below.
 *
 * <p>It runs TODAY'S (un-refactored) full mapping chain via {@link GoldenFixtureRunner#run(String)} - which
 * calls {@code EnforcedThingMapper.toWriteModel(...)} exactly mirroring the production call site at
 * {@code EnforcementFlow.computeWriteModel(...)} - for every fixture pair in
 * {@link GoldenFixtures#CASE_NAMES}, and (re)writes the committed expected file for each case as canonical
 * extended JSON ({@link JsonMode#EXTENDED}, the deterministic, fully-typed BSON-as-JSON form).
 *
 * <p><b>How to (re-)run this generator</b> (verified working - uses the exec-maven-plugin directly by full
 * coordinates, so no {@code pom.xml} wiring is needed; runs on the module's {@code test} classpath so both
 * main and test classes/dependencies are visible). <strong>Must be run with the shell's working directory
 * inside {@code thingsearch/service}</strong> (not the repo root, and not via {@code mvn -pl}): the plugin's
 * {@code java} goal executes {@code main} in-process rather than forking a subprocess, so the path
 * {@code "src/test/resources/golden/expected"} resolves against the JVM's actual {@code user.dir} - i.e.
 * wherever {@code mvn} itself was launched from - not the reactor module's {@code basedir}. Running this from
 * the repo root (or via {@code mvn -pl thingsearch/service ...} from the repo root) writes a stray
 * {@code src/test/resources/golden/expected/} tree at the repo root instead of updating the real fixtures:
 * <pre>
 * cd thingsearch/service
 * mvn test-compile \
 *     org.codehaus.mojo:exec-maven-plugin:3.5.1:java \
 *     -Dexec.mainClass=org.eclipse.ditto.thingsearch.service.persistence.write.mapping.EnforcedThingMapperGoldenGenerator \
 *     -Dexec.classpathScope=test
 * </pre>
 * Only re-run this deliberately, after confirming any resulting diff under {@code golden/expected/} is an
 * intended baseline update - the whole point of {@code EnforcedThingMapperGoldenTest} is to catch
 * <em>unintended</em> drift, including during the Phase-A backend-neutral/Mongo-encoder split that follows
 * this task.
 */
public final class EnforcedThingMapperGoldenGenerator {

    private EnforcedThingMapperGoldenGenerator() {
        throw new AssertionError();
    }

    public static void main(final String[] args) throws IOException {
        final Path expectedDir = Path.of("src", "test", "resources", "golden", "expected").toAbsolutePath();
        Files.createDirectories(expectedDir);

        for (final String caseName : GoldenFixtures.CASE_NAMES) {
            final String canonicalJson =
                    GoldenFixtureRunner.run(caseName).toJson(GoldenFixtures.CANONICAL_EXTENDED_JSON);
            final Path expectedFile = expectedDir.resolve(caseName + ".json");
            try {
                Files.writeString(expectedFile, canonicalJson + System.lineSeparator(), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
