/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Map;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Unit test for {@link HeaderEntryFilters}.
 */
public final class HeaderEntryFiltersTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(HeaderEntryFilters.class, areImmutable());
    }

    @Test
    public void toExternalHeadersFilterDiscardsDittoInternalAckRequests() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);
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

        final HeaderEntryFilter underTest = HeaderEntryFilters.toExternalHeadersFilter(headerDefinitions);

        assertThat(underTest.apply(headerDefinition.getKey(), value)).isEqualTo(expected);
    }

    @Test
    public void locationHeaderIsUsedFromExternalHeaders() {
        final DittoHeaderDefinition def = DittoHeaderDefinition.LOCATION;
        final Map<String, HeaderDefinition> headerDefinitions = Maps.newHashMap(def.getKey(), def);
        final String locationValue = "http://ditto.eclipseprojects.io/foobar";

        final HeaderEntryFilter underTest = HeaderEntryFilters.fromExternalHeadersFilter(headerDefinitions);
        assertThat(underTest.apply(def.getKey(), locationValue)).isEqualTo(locationValue);
    }

}
