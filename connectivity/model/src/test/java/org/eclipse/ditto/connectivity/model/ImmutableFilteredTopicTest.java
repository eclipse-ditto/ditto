/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableFilteredTopic}.
 */
public final class ImmutableFilteredTopicTest {

    private static final List<String> NAMESPACES =
            Collections.unmodifiableList(Lists.list("this.is.a.namespace", "eat.that", "foo.bar"));
    private static final String FILTER_EXAMPLE = "gt(attributes/a,42)";
    private static final ThingFieldSelector EXTRA_FIELDS =
            ThingFieldSelector.fromJsonFieldSelector(JsonFieldSelector.newInstance("attributes", "features/location"));

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFilteredTopic.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFilteredTopic.class,
                areImmutable(),
                provided(Topic.class, ThingFieldSelector.class).areAlsoImmutable(),
                assumingFields("namespaces").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void getTopicReturnsExpected() {
        final Topic topic = Topic.LIVE_COMMANDS;
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(topic).build();

        assertThat(underTest.getTopic()).isEqualTo(topic);
    }

    @Test
    public void getEmptyNamespacesIfNotSet() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS).build();

        assertThat(underTest.getNamespaces()).isEmpty();
    }

    @Test
    public void getNamespacesReturnsExpectedIfSet() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withNamespaces(NAMESPACES)
                .build();

        assertThat(underTest.getNamespaces()).isEqualTo(NAMESPACES);
    }

    @Test
    public void getFilterReturnsEmptyOptionalIfNotSet() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS).build();

        assertThat(underTest.getFilter()).isEmpty();
    }

    @Test
    public void getFilterReturnsExpectedIfSet() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withFilter(FILTER_EXAMPLE)
                .build();

        assertThat(underTest.getFilter()).contains(FILTER_EXAMPLE);
    }

    @Test
    public void getExtraFieldsReturnsEmptyOptionalIfNotSet() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS).build();

        assertThat(underTest.getExtraFields()).isEmpty();
    }

    @Test
    public void getExtraFieldsReturnsExpectedIfSet() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withExtraFields(EXTRA_FIELDS)
                .build();

        assertThat(underTest.getExtraFields()).contains(EXTRA_FIELDS);
    }

    @Test
    public void policyAnnouncementsOnlySupportNamespaces() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.POLICY_ANNOUNCEMENTS)
                .withNamespaces(NAMESPACES)
                .withFilter(FILTER_EXAMPLE)
                .withExtraFields(EXTRA_FIELDS)
                .build();

        assertThat(underTest.getNamespaces()).isEqualTo(NAMESPACES);
        assertThat(underTest.getFilter()).isEmpty();
        assertThat(underTest.getExtraFields()).isEmpty();
    }

    @Test
    public void connectionAnnouncementsDontSupportEnhancements() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.CONNECTION_ANNOUNCEMENTS)
                .withNamespaces(NAMESPACES)
                .withFilter(FILTER_EXAMPLE)
                .withExtraFields(EXTRA_FIELDS)
                .build();

        assertThat(underTest.getNamespaces()).isEmpty();
        assertThat(underTest.getFilter()).isEmpty();
        assertThat(underTest.getExtraFields()).isEmpty();
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS).build();

        assertThat(underTest.toString()).isEqualTo("_/_/things/twin/events");
    }

    @Test
    public void toStringReturnsExpectedWithFilterNamespacesAndExtraFields() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withNamespaces(NAMESPACES)
                .withFilter(FILTER_EXAMPLE)
                .withExtraFields(EXTRA_FIELDS)
                .build();
        final String expected = MessageFormat.format("_/_/things/twin/events?namespaces={0}&filter={1}&extraFields={2}",
                String.join(",", NAMESPACES), FILTER_EXAMPLE, EXTRA_FIELDS);

        final String actual = underTest.toString();

        assertThat(actual).hasToString(expected);
    }

    @Test
    public void toStringReturnsExpectedWithOnlyNamespace() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.LIVE_MESSAGES)
                .withNamespaces(NAMESPACES)
                .build();

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo("_/_/things/live/messages?namespaces=" + String.join(",", NAMESPACES));
    }

    @Test
    public void toStringReturnsExpectedWithOnlyFilter() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withFilter(FILTER_EXAMPLE)
                .build();

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo("_/_/things/twin/events?filter=" + FILTER_EXAMPLE);
    }

    @Test
    public void toStringReturnsExpectedWithOnlyExtraFields() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withExtraFields(EXTRA_FIELDS)
                .build();

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo("_/_/things/twin/events?extraFields=" + EXTRA_FIELDS);
    }

    @Test
    public void fromStringParsesAsExpectedWithFilterAndNamespaces() {
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withNamespaces(NAMESPACES)
                .withFilter(FILTER_EXAMPLE)
                .build();

        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopic.toString());

        assertThat(actual).isEqualTo(filteredTopic);
    }

    @Test
    public void fromStringParsesAsExpectedWithFilterNamespacesAndExtraFields() {
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withNamespaces(NAMESPACES)
                .withFilter(FILTER_EXAMPLE)
                .withExtraFields(EXTRA_FIELDS)
                .build();

        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopic.toString());

        assertThat(actual).isEqualTo(filteredTopic);
    }

    @Test
    public void fromStringParsesAsExpected() {
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS).build();

        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopic.toString());

        assertThat(actual).isEqualTo(filteredTopic);
    }

    @Test
    public void fromStringParsesAsExpectedWithOnlyNamespace() {
        final ImmutableFilteredTopic expected = ImmutableFilteredTopic.getBuilder(Topic.LIVE_COMMANDS)
                .withNamespaces(NAMESPACES)
                .build();

        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(expected.toString());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromStringParsesAsExpectedWithOnlyFilter() {
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.LIVE_EVENTS)
                .withFilter(FILTER_EXAMPLE)
                .build();

        final FilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopic.toString());

        assertThat(actual).isEqualTo(filteredTopic);
    }

    @Test
    public void fromStringParsesAsExpectedWithOnlyExtraFields() {
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withExtraFields(EXTRA_FIELDS)
                .build();

        final FilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopic.toString());

        assertThat(actual).isEqualTo(filteredTopic);
    }

}
