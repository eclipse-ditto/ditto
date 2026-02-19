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
package org.eclipse.ditto.gateway.service.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

/**
 * Unit tests for {@link NamespacePatternMatcher}.
 */
public class NamespacePatternMatcherTest {

    @Test
    public void testExactMatch() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of("org.eclipse.ditto"),
                List.of()
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("org.eclipse.other")).isFalse();
    }

    @Test
    public void testWildcardMatch() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of("org.eclipse.*"),
                List.of()
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("org.eclipse.things")).isTrue();
        assertThat(matcher.isAllowed("org.eclipse")).isFalse();
        assertThat(matcher.isAllowed("com.example.test")).isFalse();
    }

    @Test
    public void testMultipleAllowedPatterns() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of("org.eclipse.*", "com.example.*"),
                List.of()
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("com.example.test")).isTrue();
        assertThat(matcher.isAllowed("net.other.namespace")).isFalse();
    }

    @Test
    public void testBlockedPatternsHavePrecedence() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of("org.eclipse.*"),
                List.of("org.eclipse.test*")
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("org.eclipse.test.internal")).isFalse();
        assertThat(matcher.isAllowed("org.eclipse.test")).isFalse();
    }

    @Test
    public void testEmptyAllowedListAllowsAll() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of(),
                List.of()
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("com.example.test")).isTrue();
        assertThat(matcher.isAllowed("any.namespace")).isTrue();
    }

    @Test
    public void testEmptyAllowedListButWithBlocked() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of(),
                List.of("forbidden.*")
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("forbidden.namespace")).isFalse();
        assertThat(matcher.isAllowed("forbidden.test")).isFalse();
    }

    @Test
    public void testQuestionMarkWildcard() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of("org.?.ditto"),
                List.of()
        );

        assertThat(matcher.isAllowed("org.x.ditto")).isTrue();
        assertThat(matcher.isAllowed("org.eclipse.ditto")).isFalse();
        assertThat(matcher.isAllowed("org..ditto")).isFalse();
    }

    @Test
    public void testComplexPattern() {
        final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                List.of("org.eclipse.*", "com.*.test"),
                List.of("org.eclipse.internal.*", "*.blocked")
        );

        assertThat(matcher.isAllowed("org.eclipse.ditto")).isTrue();
        assertThat(matcher.isAllowed("com.example.test")).isTrue();
        assertThat(matcher.isAllowed("org.eclipse.internal.something")).isFalse();
        assertThat(matcher.isAllowed("any.namespace.blocked")).isFalse();
        assertThat(matcher.isAllowed("net.other.namespace")).isFalse();
    }

}
