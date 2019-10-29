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

package org.eclipse.ditto.model.base.entity.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Unit test for {@link RegexPatterns}.
 */
public final class RegexPatternsTest {

    private static final List<String> GOOD_NAMESPACES = Arrays.asList(
            "",
            "ditto.eclipse.org",
            "com.google",
            "Foo.bar_122_"
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

    private static final List<String> GOOD_NAMES = Arrays.asList(
            "ditto",
            "thing42",
            "-:@&=+,.!~*'_;<>$",
            "foo%2Fbar"
    );
    private static final List<String> BAD_NAMES = Arrays.asList(
            "",
            "$ditto",
            "foo/bar",
            "foo bar"
    );

    private static final List<String> BAD_IDS = Arrays.asList(
            "org.eclipse.ditto",
            "foo;bar",
            "foo%3A"
    );

    @Test
    public void entityNameRegex() {
        GOOD_NAMES.forEach(this::assertNameMatches);
        BAD_NAMES.forEach(this::assertNameNotMatches);
    }

    private void assertNameMatches(final String name) {
        assertMatches(RegexPatterns.ENTITY_NAME_PATTERN, name);
    }

    private void assertNameNotMatches(final String name) {
        assertNotMatches(RegexPatterns.ENTITY_NAME_PATTERN, name);
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
    public void idRegex() {
        GOOD_NAMESPACES.forEach(namespace -> GOOD_NAMES.forEach(name -> {
            assertIdMatches(namespace + ":" + name);
        }));
        BAD_NAMESPACES.forEach(namespace -> BAD_NAMES.forEach(name -> {
            assertIdNotMatches(namespace + ":" + name);
        }));
        BAD_IDS.forEach(this::assertIdNotMatches);
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
