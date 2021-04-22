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
package org.eclipse.ditto.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Map;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ReadJsonArrayHeadersFilter}.
 */
public final class ReadJsonArrayHeadersFilterTest {

    private static final DittoHeaderDefinition HEADER_DEFINITION = DittoHeaderDefinition.REQUESTED_ACKS;

    private Map<String, HeaderDefinition> headerDefinitions;

    @Before
    public void setUp() {
        headerDefinitions = Maps.newHashMap(HEADER_DEFINITION.getKey(), HEADER_DEFINITION);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ReadJsonArrayHeadersFilter.class,
                areImmutable(),
                assumingFields("headerDefinitions").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void filterNonJsonArrayValue() {
        final String value = "bar";
        final ReadJsonArrayHeadersFilter underTest = ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);

        assertThat(underTest.apply("foo", value)).isEqualTo(value);
    }

    @Test
    public void filterJsonArrayStringRepresentation() {
        final List<AcknowledgementRequest> acknowledgementRequests = Lists.list(
                AcknowledgementRequest.of(AcknowledgementLabel.of("foo")),
                AcknowledgementRequest.of(AcknowledgementLabel.of("bar")));
        final JsonArray acknowledgementRequestsJsonArray = acknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        final String jsonArrayStringRepresentation = acknowledgementRequestsJsonArray.toString();
        final ReadJsonArrayHeadersFilter underTest = ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);

        final String filteredValue = underTest.filterValue(HEADER_DEFINITION.getKey(), jsonArrayStringRepresentation);

        assertThat(filteredValue).isEqualTo(jsonArrayStringRepresentation);
    }

    @Test
    public void filterJsonArrayValueBasedOnCommaSeparatedList() {
        final List<AcknowledgementRequest> acknowledgementRequests = Lists.list(
                AcknowledgementRequest.of(AcknowledgementLabel.of("foo")),
                AcknowledgementRequest.of(AcknowledgementLabel.of("bar")));
        final JsonArray acknowledgementRequestsJsonArray = acknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        final String expectedArrayValue = acknowledgementRequestsJsonArray.toString();
        final String commaSeparatedRequestedAcks = "foo,bar";

        final ReadJsonArrayHeadersFilter underTest = ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);

        final String filteredValue = underTest.apply(HEADER_DEFINITION.getKey(), commaSeparatedRequestedAcks);

        assertThat(filteredValue).isEqualTo(expectedArrayValue);
    }

    @Test
    public void filterJsonArrayValueBasedOnEmptyString() {
        final JsonArray emptyJsonArray = JsonArray.empty();
        final String expected = emptyJsonArray.toString();

        final ReadJsonArrayHeadersFilter underTest = ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);

        final String filtered = underTest.apply(HEADER_DEFINITION.getKey(), "");

        assertThat(filtered).isEqualTo(expected);
    }

}
