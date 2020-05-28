/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.model.base.entity.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Unit test for {@link RegexPatterns}.
 */
public final class RegexPatternsTest {

    /**
     * Also applies to entity name (thing and policy name), attributes, features & policy label
     */
    private static final List<String> BAD_FEATURE_IDS = List.of(
            // slashes
            "/", "//", "/abc", "abc/", "//abc", "abc//", "abc/abc", "abc//abc",
            // forbidden special characters
            "§", "°", "´",
            // whitespaces
            "\t", "\n",
            // examples
            "3°C", "´a", "§1", "foo/bar"
    );

    private static final List<String> GOOD_FEATURE_IDS = List.of(
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
    private static final List<String> BAD_MESSAGE_SUBJECTS = List.of(
            // forbidden special characters
            "§", "°", "´",
            // whitespaces
            "\t", "\n",
            // examples
            "3°C", "´a", "§1"
    );

    private static final List<String> GOOD_MESSAGE_SUBJECTS = List.of(
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
            ".org"
    );

    private static final List<String> GOOD_NAMESPACES = Arrays.asList(
            "",
            "ditto.eclipse.org",
            "com.google",
            "Foo.bar_122_"
    );

    @Test
    public void entityNameRegex() {
        GOOD_FEATURE_IDS.forEach(this::assertNameMatches);
        BAD_FEATURE_IDS.forEach(this::assertNameNotMatches);
    }

    private void assertNameMatches(final String name) {
        assertMatches(RegexPatterns.ENTITY_NAME_PATTERN, name);
    }

    private void assertNameNotMatches(final String name) {
        assertNotMatches(RegexPatterns.ENTITY_NAME_PATTERN, name);
    }

    @Test
    public void featureRegex() {
        GOOD_FEATURE_IDS.forEach(this::assertFeatureMatches);
        BAD_FEATURE_IDS.forEach(this::assertFeatureNotMatches);
    }

    private void assertFeatureMatches(final String id) {
        assertMatches(RegexPatterns.FEATURE_PATTERN, id);
    }

    private void assertFeatureNotMatches(final String id) {
        assertNotMatches(RegexPatterns.FEATURE_PATTERN, id);
    }

    @Test
    public void namespaceRegex() {
        GOOD_NAMESPACES.forEach(this::assertNamespaceMatches);
        BAD_NAMESPACES.forEach(this::assertNamespaceNotMatches);
    }

    private void assertNamespaceMatches(final String namespace) {
        assertMatches(RegexPatterns.NAMESPACE_PATTERN, namespace);
    }

    private void assertNamespaceNotMatches(final String namespace) {
        assertNotMatches(RegexPatterns.NAMESPACE_PATTERN, namespace);
    }

    @Test
    public void subjectRegex() {
        GOOD_MESSAGE_SUBJECTS.forEach(this::assertSubjectMatches);
        BAD_MESSAGE_SUBJECTS.forEach(this::assertSubjectNotMatches);
    }

    private void assertSubjectMatches(final String subject) {
        assertMatches(RegexPatterns.SUBJECT_REGEX, subject);
    }

    private void assertSubjectNotMatches(final String subject) {
        assertNotMatches(RegexPatterns.SUBJECT_REGEX, subject);
    }

    @Test
    public void idRegex() {
        GOOD_NAMESPACES.forEach(namespace -> GOOD_FEATURE_IDS.forEach(name -> {
            assertIdMatches(namespace + ":" + name);
        }));
        BAD_NAMESPACES.forEach(namespace -> BAD_FEATURE_IDS.forEach(name -> {
            assertIdNotMatches(namespace + ":" + name);
        }));
        BAD_FEATURE_IDS.forEach(this::assertIdNotMatches);
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
