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
package org.eclipse.ditto.protocoladapter;

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
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.junit.Test;

/**
 * Unit test for {@link ReadJsonArrayHeadersFilter}.
 */
public final class ReadJsonArrayHeadersFilterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ReadJsonArrayHeadersFilter.class, areImmutable(),
                assumingFields("headerDefinitions").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void filterNonJsonArrayValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);
        final String key = "foo";
        final String value = "bar";
        final ReadJsonArrayHeadersFilter underTest = ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);

        assertThat(underTest.apply(key, value)).isEqualTo(value);
    }

    @Test
    public void filterJsonArrayValueBasedOnCommaSeparatedList() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);

        final List<AcknowledgementRequest> acknowledgementRequests = Lists.list(
                AcknowledgementRequest.of(AcknowledgementLabel.of("foo")),
                AcknowledgementRequest.of(AcknowledgementLabel.of("bar")));
        final JsonArray acknowledgementRequestsJsonArray = acknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        final String expectedArrayValue = acknowledgementRequestsJsonArray.toString();

        final ReadJsonArrayHeadersFilter underTest = ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);

        // ensure that also the string representation of a JsonArray may be passed in:
        assertThat(underTest.apply(headerDefinition.getKey(), expectedArrayValue)).isEqualTo(expectedArrayValue);

        // but also the comma separated list must work:
        final String commaSeparatedRequestedAcks = "foo,bar";
        assertThat(underTest.apply(headerDefinition.getKey(), commaSeparatedRequestedAcks))
                .isEqualTo(expectedArrayValue);
    }

}