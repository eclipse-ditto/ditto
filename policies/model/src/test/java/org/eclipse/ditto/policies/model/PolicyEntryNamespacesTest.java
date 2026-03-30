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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Unit test for {@link PolicyEntryNamespaces}.
 */
public final class PolicyEntryNamespacesTest {

    @Test
    public void fromJsonArrayReturnsValidatedNamespaces() {
        final JsonArray jsonArray = JsonArray.of(JsonValue.of("com.acme"), JsonValue.of("com.acme.*"));

        final java.util.List<String> namespaces = PolicyEntryNamespaces.fromJsonArray(jsonArray);

        assertThat(namespaces).containsExactly("com.acme", "com.acme.*");
    }

    @Test
    public void fromJsonArrayRejectsNonStringValues() {
        final JsonArray jsonArray = JsonArray.of(JsonValue.of(23), JsonValue.of("com.acme"));

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> PolicyEntryNamespaces.fromJsonArray(jsonArray));
    }

    @Test
    public void validateRejectsBareWildcard() {
        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> PolicyEntryNamespaces.validate(
                        Arrays.asList("com.acme", "*")));
    }

    @Test
    public void emptyNamespacePatternsAreAccepted() {
        PolicyEntryNamespaces.validate(Collections.emptyList());
    }

    @Test
    public void matchesPatternMatchesWildcardChildrenButNotRoot() {
        assertThat(PolicyEntryNamespaces.matchesPattern("com.acme.*", "com.acme.devices"))
                .isTrue();
        assertThat(PolicyEntryNamespaces.matchesPattern("com.acme.*", "com.acme"))
                .isFalse();
        assertThat(PolicyEntryNamespaces.matchesPattern("com.acme", "com.acme"))
                .isTrue();
    }

    @Test
    public void matchesTreatsEmptyPatternsAsMatchAll() {
        assertThat(PolicyEntryNamespaces.matches(Collections.emptyList(), "com.acme")).isTrue();
    }

    @Test
    public void matchesSupportsExactAndWildcardSyntax() {
        assertThat(PolicyEntryNamespaces.matches(Collections.singletonList("com.acme"), "com.acme")).isTrue();
        assertThat(PolicyEntryNamespaces.matches(Collections.singletonList("com.acme"), "com.acme.vehicles")).isFalse();
        assertThat(PolicyEntryNamespaces.matches(Collections.singletonList("com.acme.*"), "com.acme")).isFalse();
        assertThat(PolicyEntryNamespaces.matches(Collections.singletonList("com.acme.*"), "com.acme.vehicles")).isTrue();
    }

}
