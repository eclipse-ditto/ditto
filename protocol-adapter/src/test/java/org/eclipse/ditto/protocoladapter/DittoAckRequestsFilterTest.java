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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.junit.Test;

/**
 * Unit test for {@link DittoAckRequestsFilter}.
 */
public final class DittoAckRequestsFilterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoAckRequestsFilter.class, areImmutable());
    }

    @Test
    public void filterValueWithoutMatchingHeaderDefinition() {
        final String key = "foo";
        final String value = "bar";
        final DittoAckRequestsFilter underTest = DittoAckRequestsFilter.getInstance();

        assertThat(underTest.apply(key, value)).isEqualTo(value);
    }

    @Test
    public void filterValueWithMatchingHeaderDefinitionWithoutDittoInternalRequests() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final List<AcknowledgementRequest> acknowledgementRequests = Lists.list(
                AcknowledgementRequest.of(AcknowledgementLabel.of("foo")),
                AcknowledgementRequest.of(AcknowledgementLabel.of("bar")));
        final JsonArray acknowledgementRequestsJsonArray = acknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        final String value = acknowledgementRequestsJsonArray.toString();

        final DittoAckRequestsFilter underTest = DittoAckRequestsFilter.getInstance();

        assertThat(underTest.apply(headerDefinition.getKey(), value)).isEqualTo(value);
    }

    @Test
    public void filterValueWithMatchingHeaderDefinitionWithDittoInternalRequests() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final List<AcknowledgementRequest> allAcknowledgementRequests = Lists.list(
                AcknowledgementRequest.of(AcknowledgementLabel.of("foo")),
                AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                AcknowledgementRequest.of(AcknowledgementLabel.of("bar")));
        final JsonArray allAcknowledgementRequestsJsonArray = allAcknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        final String value = allAcknowledgementRequestsJsonArray.toString();
        final JsonArray externalAcknowledgementRequests = allAcknowledgementRequestsJsonArray.toBuilder()
                .remove(1)
                .build();
        final String expected = externalAcknowledgementRequests.toString();

        final DittoAckRequestsFilter underTest = DittoAckRequestsFilter.getInstance();

        assertThat(underTest.apply(headerDefinition.getKey(), value)).isEqualTo(expected);
    }

}