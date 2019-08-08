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
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.eclipse.ditto.signals.commands.connectivity.TestConstants.ID;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashMap;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants.Metrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse.JsonFields;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveConnectionMetricsResponse}.
 */
public final class RetrieveConnectionMetricsResponseTest {

    private static final ConnectionMetrics METRICS = ConnectivityModelFactory.newConnectionMetrics(
            ConnectivityModelFactory.newAddressMetric(Collections.emptySet()),
            ConnectivityModelFactory.newAddressMetric(Collections.emptySet()));

    private static final SourceMetrics EMPTY_SOURCE_METRICS =
            ConnectivityModelFactory.newSourceMetrics(new HashMap<>());

    private static final TargetMetrics EMPTY_TARGET_METRICS =
            ConnectivityModelFactory.newTargetMetrics(new HashMap<>());

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, RetrieveConnectionMetricsResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, ID.toString())
            .set(JsonFields.CONTAINS_FAILURES, false)
            .set(JsonFields.CONNECTION_METRICS, Metrics.Json.CONNECTION_METRICS_JSON)
            .set(JsonFields.SOURCE_METRICS, Metrics.SOURCE_METRICS1.toJson())
            .set(JsonFields.TARGET_METRICS, Metrics.TARGET_METRICS1.toJson())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveConnectionMetricsResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnectionMetricsResponse.class, areImmutable(),
                provided(JsonObject.class, EntityId.class).isAlsoImmutable());
    }

    @Test
    public void retrieveInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> RetrieveConnectionMetricsResponse.getBuilder(null, DittoHeaders.empty())
                                .connectionMetrics(METRICS)
                                .sourceMetrics(EMPTY_SOURCE_METRICS)
                                .targetMetrics(EMPTY_TARGET_METRICS)
                                .build())
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionMetricsResponse expected =
                RetrieveConnectionMetricsResponse.getBuilder(ID, DittoHeaders.empty())
                        .connectionMetrics(Metrics.CONNECTION_METRICS)
                        .sourceMetrics(Metrics.SOURCE_METRICS1)
                        .targetMetrics(Metrics.TARGET_METRICS1)
                        .build();

        final RetrieveConnectionMetricsResponse actual =
                RetrieveConnectionMetricsResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveConnectionMetricsResponse.getBuilder(ID, DittoHeaders.empty())
                        .connectionMetrics(Metrics.CONNECTION_METRICS)
                        .sourceMetrics(Metrics.SOURCE_METRICS1)
                        .targetMetrics(Metrics.TARGET_METRICS1)
                        .build().toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath =
                JsonFactory.newPointer("/metrics");

        final RetrieveConnectionMetricsResponse underTest =
                RetrieveConnectionMetricsResponse.getBuilder(ID, DittoHeaders.empty())
                .connectionMetrics(Metrics.CONNECTION_METRICS)
                .sourceMetrics(Metrics.SOURCE_METRICS1)
                 .targetMetrics(Metrics.TARGET_METRICS1)
                .build();

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
