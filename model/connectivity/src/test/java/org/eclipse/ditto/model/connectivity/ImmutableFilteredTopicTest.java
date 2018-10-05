/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableFilteredTopic}.
 */
public class ImmutableFilteredTopicTest {

    private static final String FILTER_EXAMPLE = "gt(attributes/a,42)";

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFilteredTopic.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFilteredTopic.class, areImmutable(),
                provided(Topic.class).isAlsoImmutable());
    }

    @Test
    public void toStringReturnsExpected() {
        final FilteredTopic filteredTopic = ImmutableFilteredTopic.of(Topic.TWIN_EVENTS, Collections.emptyList(), null);
        final String actual = filteredTopic.toString();
        assertThat(actual).isEqualTo("_/_/things/twin/events");
    }

    @Test
    public void toStringReturnsExpectedWithFilterAndNamespaces() {
        final List<String> namespaces = Arrays.asList("foo", "bar");
        final FilteredTopic filteredTopic = ImmutableFilteredTopic.of(Topic.TWIN_EVENTS, namespaces, FILTER_EXAMPLE);
        final String actual = filteredTopic.toString();
        assertThat(actual).isEqualTo(
                "_/_/things/twin/events?namespaces=" + String.join(",", namespaces) + "&filter=" + FILTER_EXAMPLE);
    }

    @Test
    public void toStringReturnsExpectedWithOnlyNamespace() {
        final List<String> namespaces = Arrays.asList("this.is.a.namespace", "eat.that", "foo.bar");
        final FilteredTopic filteredTopic = ImmutableFilteredTopic.of(Topic.LIVE_MESSAGES, namespaces, null);
        final String actual = filteredTopic.toString();
        assertThat(actual).isEqualTo("_/_/things/live/messages?namespaces=" + String.join(",", namespaces));
    }

    @Test
    public void toStringReturnsExpectedWithOnlyFilter() {
        final FilteredTopic filteredTopic =
                ImmutableFilteredTopic.of(Topic.TWIN_EVENTS, Collections.emptyList(), FILTER_EXAMPLE);
        final String actual = filteredTopic.toString();
        assertThat(actual).isEqualTo("_/_/things/twin/events?filter=" + FILTER_EXAMPLE);
    }

    @Test
    public void fromStringParsesAsExpectedWithFilterAndNamespaces() {
        final String filterTopicString = "_/_/things/twin/events?namespaces=foo,bar&filter=" + FILTER_EXAMPLE;

        final FilteredTopic filteredTopic = ImmutableFilteredTopic.fromString(filterTopicString);
        assertThat(filteredTopic.getNamespaces()).isEqualTo(Arrays.asList("foo", "bar"));
        assertThat(filteredTopic.getTopic()).isEqualTo(Topic.TWIN_EVENTS);
        assertThat(filteredTopic.getFilter()).contains(FILTER_EXAMPLE);
    }

    @Test
    public void fromStringParsesAsExpected() {
        final String filterTopicString = "_/_/things/twin/events";

        final FilteredTopic filteredTopic = ImmutableFilteredTopic.fromString(filterTopicString);
        assertThat(filteredTopic.getNamespaces()).isEmpty();
        assertThat(filteredTopic.getTopic()).isEqualTo(Topic.TWIN_EVENTS);
        assertThat(filteredTopic.getFilter()).isEmpty();
    }

    @Test
    public void fromStringParsesAsExpectedWithOnlyNamespace() {
        final List<String> namespaces = Arrays.asList("this.is.a.namespace", "eat.that", "foo.bar");
        final String filterTopicString = "_/_/things/live/commands?namespaces=" + String.join(",", namespaces);

        final FilteredTopic filteredTopic = ImmutableFilteredTopic.fromString(filterTopicString);
        assertThat(filteredTopic.getNamespaces()).isEqualTo(namespaces);
        assertThat(filteredTopic.getTopic()).isEqualTo(Topic.LIVE_COMMANDS);
    }

    @Test
    public void fromStringParsesAsExpectedWithOnlyFilter() {
        final String filterTopicString = "_/_/things/live/events?filter=" + FILTER_EXAMPLE;

        final FilteredTopic filteredTopic = ImmutableFilteredTopic.fromString(filterTopicString);
        assertThat(filteredTopic.getNamespaces()).isEmpty();
        assertThat(filteredTopic.getTopic()).isEqualTo(Topic.LIVE_EVENTS);
        assertThat(filteredTopic.getFilter()).contains(FILTER_EXAMPLE);
    }

}
