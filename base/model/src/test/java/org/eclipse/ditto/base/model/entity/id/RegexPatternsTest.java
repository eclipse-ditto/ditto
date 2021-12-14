/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mutabilitydetector.internal.com.google.common.collect.Streams;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns}.
 */
public final class RegexPatternsTest {

    /**
     * Also applies to entity name (thing and policy name), attributes, features & policy label
     */
    private static final List<String> BAD_FEATURE_IDS = Arrays.asList(
            // slashes
            "/", "//", "/abc", "abc/", "//abc", "abc//", "abc/abc", "abc//abc",
            // forbidden special characters
            "§", "°", "´",
            // whitespaces
            "\t", "\n",
            // examples
            "3°C", "´a", "§1", "foo/bar"
    );

    private static final List<String> GOOD_FEATURE_IDS = Arrays.asList(
            // allowed special characters
            "!\"$%&()=?`*+~'#_-:.;,|<>\\{}[]^",
            // whitespaces
            " ", "        ", "a b c",
            // examples
            "a", "a!", "$a", "&a", "?a", "a.:b?c(de)-f%g\"h\"\\jk|lm#p", "\"a\"", "{\"property\":123}", "foo%3A", "$foo"
    );

    /**
     * Also applies to policy resource & policy subject
     */
    private static final List<String> BAD_MESSAGE_SUBJECTS = Arrays.asList(
            // forbidden special characters
            "§", "°", "´",
            // whitespaces
            "\t", "\n",
            // examples
            "3°C", "´a", "§1"
    );

    private static final List<String> GOOD_MESSAGE_SUBJECTS = Arrays.asList(
            // slashes
            "/", "//", "/abc", "abc/", "//abc", "abc//", "abc/abc", "abc//abc",
            // allowed special characters
            "!\"$%&()=?`*+~'#_-:.;,|<>\\{}[]^/",
            // examples
            "a", "a!", "$a", "&a", "?a", "a.:b?c(de)-f%g\"h\"\\jk|lm#p", "\"a\"", "{\"property\":123}"
    );

    private static final List<String> BAD_NAMESPACES = Arrays.asList(
            "org.eclipse.",
            ".org.eclipse",
            "_org.eclipse",
            "org._eclipse",
            "1org.eclipse",
            "org.1eclipse",
            "org.ec lipse",
            "org.",
            ".org",
            "$test",
            "-foo",
            "foo.-bar",
            "foo--bar"
    );

    private static final List<String> GOOD_NAMESPACES = Arrays.asList(
            "",
            "ditto.eclipseprojects.io",
            "com.google",
            "Foo.bar_122_",
            "foo-bar.io"
    );

    @Test
    public void entityNameRegex() {
        Assertions.assertThat(GOOD_FEATURE_IDS).allSatisfy(this::assertNameMatches);
        Assertions.assertThat(BAD_FEATURE_IDS).allSatisfy(this::assertNameNotMatches);
    }

    private void assertNameMatches(final String name) {
        assertMatches(RegexPatterns.ENTITY_NAME_PATTERN, name);
    }

    private void assertNameNotMatches(final String name) {
        assertNotMatches(RegexPatterns.ENTITY_NAME_PATTERN, name);
    }

    @Test
    public void featureRegex() {
        Assertions.assertThat(GOOD_FEATURE_IDS).allSatisfy(this::assertFeatureMatches);
        Assertions.assertThat(BAD_FEATURE_IDS).allSatisfy(this::assertFeatureNotMatches);
    }

    private void assertFeatureMatches(final String id) {
        assertMatches(RegexPatterns.NO_CONTROL_CHARS_NO_SLASHES_PATTERN, id);
    }

    private void assertFeatureNotMatches(final String id) {
        assertNotMatches(RegexPatterns.NO_CONTROL_CHARS_NO_SLASHES_PATTERN, id);
    }

    @Test
    public void namespaceRegex() {
        Assertions.assertThat(GOOD_NAMESPACES).allSatisfy(this::assertNamespaceMatches);
        Assertions.assertThat(BAD_NAMESPACES).allSatisfy(this::assertNamespaceNotMatches);
    }

    private void assertNamespaceMatches(final String namespace) {
        assertMatches(RegexPatterns.NAMESPACE_PATTERN, namespace);
    }

    private void assertNamespaceNotMatches(final String namespace) {
        assertNotMatches(RegexPatterns.NAMESPACE_PATTERN, namespace);
    }

    @Test
    public void subjectRegex() {
        Assertions.assertThat(GOOD_MESSAGE_SUBJECTS).allSatisfy(this::assertSubjectMatches);
        Assertions.assertThat(BAD_MESSAGE_SUBJECTS).allSatisfy(this::assertSubjectNotMatches);
    }

    private void assertSubjectMatches(final String subject) {
        assertMatches(RegexPatterns.NO_CONTROL_CHARS_PATTERN, subject);
    }

    private void assertSubjectNotMatches(final String subject) {
        assertNotMatches(RegexPatterns.NO_CONTROL_CHARS_PATTERN, subject);
    }

    @Test
    public void idRegex() {

        Assertions.assertThat(Streams.zip(
                GOOD_NAMESPACES.stream(), GOOD_FEATURE_IDS.stream(),
                (ns, id) -> ns + ":" + id))
                .allSatisfy(this::assertIdMatches);

        Assertions.assertThat(Streams.zip(
                        BAD_NAMESPACES.stream(), BAD_FEATURE_IDS.stream(),
                        (ns, id) -> ns + ":" + id))
                .allSatisfy(this::assertIdNotMatches);

        Assertions.assertThat(BAD_FEATURE_IDS).allSatisfy(this::assertIdNotMatches);
    }

    private void assertIdMatches(final String id) {
        assertMatches(RegexPatterns.ID_PATTERN, id);
    }

    private void assertIdNotMatches(final String id) {
        assertNotMatches(RegexPatterns.ID_PATTERN, id);
    }

    private void assertMatches(final Pattern pattern, final String toMatch) {
        assertThat(matches(pattern, toMatch)).isTrue();
    }

    private void assertNotMatches(final Pattern pattern, final String toMatch) {
        assertThat(matches(pattern, toMatch)).isFalse();
    }

    private boolean matches(final Pattern pattern, final String toMatch) {
        return pattern.matcher(toMatch).matches();
    }
}
