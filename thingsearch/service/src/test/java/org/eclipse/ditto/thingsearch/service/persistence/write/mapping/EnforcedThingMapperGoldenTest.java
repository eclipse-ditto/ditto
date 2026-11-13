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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.json.JsonMode;
import org.junit.Test;

/**
 * Golden-file regression test for {@link EnforcedThingMapper} (Task A1 - pre-refactor BSON baseline).
 *
 * <p>For every case in {@link GoldenFixtures#CASE_NAMES} this test runs TODAY'S full mapping chain via
 * {@link GoldenFixtureRunner#run(String)} and asserts the resulting {@code BsonDocument}, rendered as
 * canonical extended JSON ({@link JsonMode#EXTENDED}), is byte-for-byte identical to the committed expected
 * file under {@code src/test/resources/golden/expected/<case>.json}.
 *
 * <p>This test must stay green across the upcoming Phase-A split of {@code EnforcedThingMapper} into a
 * backend-neutral document builder plus a Mongo BSON encoder - it is the correctness proof that the split
 * preserves today's exact wire format. A failure here means the refactor changed observable BSON output;
 * fixtures must never be touched to "fix" a failure without confirming the change is intended (see
 * {@code EnforcedThingMapperGoldenGenerator} for how the expected files were produced).
 */
public final class EnforcedThingMapperGoldenTest {

    @Test
    public void nestedScalars() {
        assertCaseMatchesGoldenFile("nested-scalars");
    }

    @Test
    public void arraysMixed() {
        assertCaseMatchesGoldenFile("arrays-mixed");
    }

    @Test
    public void featuresMulti() {
        assertCaseMatchesGoldenFile("features-multi");
    }

    @Test
    public void specialCharsKeys() {
        assertCaseMatchesGoldenFile("special-chars-keys");
    }

    @Test
    public void unicodeKeysValues() {
        assertCaseMatchesGoldenFile("unicode-keys-values");
    }

    @Test
    public void indexLengthRestriction() {
        assertCaseMatchesGoldenFile("index-length-restriction");
    }

    @Test
    public void authGrantRevokeMultiLevel() {
        assertCaseMatchesGoldenFile("auth-grant-revoke-multi-level");
    }

    @Test
    public void authRevokeBelowGrant() {
        assertCaseMatchesGoldenFile("auth-revoke-below-grant");
    }

    @Test
    public void authFeatureLevelGrants() {
        assertCaseMatchesGoldenFile("auth-feature-level-grants");
    }

    @Test
    public void authMultipleSubjects() {
        assertCaseMatchesGoldenFile("auth-multiple-subjects");
    }

    @Test
    public void importedReferencedPolicies() {
        assertCaseMatchesGoldenFile("imported-referenced-policies");
    }

    @Test
    public void authNamespaceScopedEntries() {
        assertCaseMatchesGoldenFile("auth-namespace-scoped-entries");
    }

    @Test
    public void thingWithoutPolicy() {
        assertCaseMatchesGoldenFile("thing-without-policy");
    }

    @Test
    public void allCoverageMatrixCasesHaveAGoldenFile() {
        // Guards against a case being added to/removed from GoldenFixtures.CASE_NAMES without a matching
        // fixture directory on the classpath and without a corresponding per-case @Test method here (in
        // either direction), silently dropping or duplicating coverage.
        assertThat(GoldenFixtures.CASE_NAMES).hasSize(13);

        final Set<String> fixtureDirNames = fixtureDirectoryNamesOnClasspath();
        assertThat(fixtureDirNames)
                .describedAs("fixture directories under golden/ on the classpath vs. GoldenFixtures.CASE_NAMES")
                .isEqualTo(Set.copyOf(GoldenFixtures.CASE_NAMES));

        final Set<String> testMethodNames = Stream.of(EnforcedThingMapperGoldenTest.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());
        for (final String caseName : GoldenFixtures.CASE_NAMES) {
            final String expectedMethodName = toCamelCase(caseName);
            assertThat(testMethodNames)
                    .describedAs("expected a @Test method <%s> exercising golden case <%s>",
                            expectedMethodName, caseName)
                    .contains(expectedMethodName);
        }
    }

    /**
     * Lists the sub-directory names directly under the {@code golden/} classpath resource root, excluding
     * {@code expected} (the generator's output directory, not a fixture pair).
     */
    private static Set<String> fixtureDirectoryNamesOnClasspath() {
        final URL goldenDirUrl = EnforcedThingMapperGoldenTest.class.getClassLoader().getResource("golden");
        if (goldenDirUrl == null) {
            throw new IllegalStateException("Missing classpath resource directory: golden");
        }
        final Path goldenDir;
        try {
            goldenDir = Path.of(goldenDirUrl.toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException("Malformed classpath resource URL for golden/: " + goldenDirUrl, e);
        }
        try (Stream<Path> entries = Files.list(goldenDir)) {
            return entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !"expected".equals(name))
                    .collect(Collectors.toSet());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Converts a kebab-case fixture case name (e.g. {@code "auth-grant-revoke-multi-level"}) to the camelCase
     * test-method name convention used in this class (e.g. {@code "authGrantRevokeMultiLevel"}).
     */
    private static String toCamelCase(final String caseName) {
        final List<String> parts = List.of(caseName.split("-"));
        final StringBuilder result = new StringBuilder(parts.get(0));
        for (final String part : parts.subList(1, parts.size())) {
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return result.toString();
    }

    private void assertCaseMatchesGoldenFile(final String caseName) {
        final String actualJson = GoldenFixtureRunner.run(caseName).toJson(GoldenFixtures.CANONICAL_EXTENDED_JSON);
        final String expectedJson = readExpectedFile(caseName);
        assertThat(actualJson).describedAs("BSON output for golden case <%s>", caseName)
                .isEqualTo(expectedJson);
    }

    private static String readExpectedFile(final String caseName) {
        final String resourcePath = "golden/expected/" + caseName + ".json";
        final InputStream in = EnforcedThingMapperGoldenTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing golden expected-file resource: " + resourcePath);
        }
        try (in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            // strip the single trailing newline the generator appends, matching String concatenation on write
            final String content = out.toString(StandardCharsets.UTF_8);
            return content.endsWith(System.lineSeparator())
                    ? content.substring(0, content.length() - System.lineSeparator().length())
                    : content;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
