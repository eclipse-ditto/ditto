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

import java.text.MessageFormat;
import java.util.Arrays;
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

    @Test
    public void fromStringParsesAsExpectedWithNamespacesExtraFieldsAndRqlAndPipelineFilters() {
        final String rqlFilter = "gt(attributes/counter,42)";
        final String pipelineFilter = "fn:filter(header:ditto-originator,'ne','some:subject')";
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withNamespaces(Lists.list("ns1", "ns2"))
                .withExtraFields(ThingFieldSelector.fromString("attributes"))
                .withFilters(Arrays.asList(rqlFilter, pipelineFilter))
                .build();
        final String filteredTopicString = filteredTopic.toString();

        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopicString);

        assertThat(filteredTopicString).contains("filter=" + rqlFilter + "&filter=" + pipelineFilter);
        assertThat(actual.getFilters()).containsExactly(rqlFilter, pipelineFilter);
        assertThat(actual.toString()).isEqualTo(filteredTopicString);
        assertThat(actual).isEqualTo(filteredTopic);
    }

    @Test
    public void getFiltersReturnsAllInInsertionOrder() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withFilters(Arrays.asList("fn:filter(header:b,'exists')", FILTER_EXAMPLE, "fn:filter(header:a,'exists')"))
                .build();

        assertThat(underTest.getFilters())
                .containsExactly("fn:filter(header:b,'exists')", FILTER_EXAMPLE, "fn:filter(header:a,'exists')");
    }

    @Test
    public void getFilterReturnsFirstOfMultipleFilters() {
        // contract of the deprecated single-filter accessor: the FIRST filter in insertion order
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withFilters(Arrays.asList(FILTER_EXAMPLE, "fn:filter(header:a,'exists')"))
                .build();

        assertThat(underTest.getFilter()).contains(FILTER_EXAMPLE);
    }

    @Test
    public void withFilterReplacesPreviouslySetFilters() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withFilters(Arrays.asList("fn:filter(header:a,'exists')", "fn:filter(header:b,'exists')"))
                .withFilter(FILTER_EXAMPLE)
                .build();

        assertThat(underTest.getFilters()).containsExactly(FILTER_EXAMPLE);
    }

    @Test
    public void withFiltersNullResetsFilters() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withFilters(Collections.singletonList(FILTER_EXAMPLE))
                .withFilters(null)
                .build();

        assertThat(underTest.getFilters()).isEmpty();
        assertThat(underTest.getFilter()).isEmpty();
    }

    @Test
    public void toStringEmitsOneFilterParamPerEntryInOrder() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.TWIN_EVENTS)
                .withNamespaces(NAMESPACES)
                .withFilters(Arrays.asList(FILTER_EXAMPLE, "fn:filter(header:a,'exists')"))
                .withExtraFields(EXTRA_FIELDS)
                .build();

        assertThat(underTest.toString()).isEqualTo(
                "_/_/things/twin/events?namespaces=" + String.join(",", NAMESPACES)
                        + "&filter=" + FILTER_EXAMPLE
                        + "&filter=fn:filter(header:a,'exists')"
                        + "&extraFields=" + EXTRA_FIELDS);
    }

    @Test
    public void fromStringCollectsRepeatedFilterParamsInOrder() {
        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(
                "_/_/things/twin/events?filter=fn:filter(header:a,'exists')&filter=" + FILTER_EXAMPLE);

        assertThat(actual.getFilters()).containsExactly("fn:filter(header:a,'exists')", FILTER_EXAMPLE);
    }

    @Test
    public void fromStringToStringRoundTripsWithMultipleFilters() {
        final ImmutableFilteredTopic filteredTopic = ImmutableFilteredTopic.getBuilder(Topic.LIVE_MESSAGES)
                .withFilters(Arrays.asList("fn:filter(header:ditto-originator,'ne','some:subject')",
                        "fn:filter(header:ditto-origin,'ne','some-connection')"))
                .build();

        final ImmutableFilteredTopic actual = ImmutableFilteredTopic.fromString(filteredTopic.toString());

        assertThat(actual).isEqualTo(filteredTopic);
        assertThat(actual.toString()).isEqualTo(filteredTopic.toString());
    }

    @Test
    public void fromStringDuplicateNamespacesParamStillThrows() {
        // freezes the (out-of-scope) pre-existing behavior: only the "filter" query parameter is repeatable,
        // any other duplicated parameter keeps failing like the previous Collectors.toMap-based parsing did
        org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ImmutableFilteredTopic.fromString(
                        "_/_/things/twin/events?namespaces=ns1&namespaces=ns2"))
                .withMessageContaining("Duplicate key");
    }

    @Test
    public void announcementTopicsDropFiltersSetViaWithFilters() {
        final ImmutableFilteredTopic underTest = ImmutableFilteredTopic.getBuilder(Topic.POLICY_ANNOUNCEMENTS)
                .withFilters(Arrays.asList(FILTER_EXAMPLE, "fn:filter(header:a,'exists')"))
                .build();

        assertThat(underTest.getFilters()).isEmpty();
    }

}
