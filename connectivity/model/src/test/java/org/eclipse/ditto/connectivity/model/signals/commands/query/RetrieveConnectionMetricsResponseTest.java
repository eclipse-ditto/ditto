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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.eclipse.ditto.base.model.signals.commands.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashMap;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.SourceMetrics;
import org.eclipse.ditto.connectivity.model.TargetMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse.JsonFields;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
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
            .set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .set(JsonFields.CONTAINS_FAILURES, false)
            .set(JsonFields.CONNECTION_METRICS, TestConstants.Metrics.Json.CONNECTION_METRICS_JSON)
            .set(JsonFields.SOURCE_METRICS, TestConstants.Metrics.SOURCE_METRICS1.toJson())
            .set(JsonFields.TARGET_METRICS, TestConstants.Metrics.TARGET_METRICS1.toJson())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveConnectionMetricsResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnectionMetricsResponse.class,
                areImmutable(),
                provided(JsonObject.class, ConnectionId.class).isAlsoImmutable());
    }

    @Test
    public void retrieveInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionMetricsResponse.getBuilder(null, DittoHeaders.empty())
                        .connectionMetrics(METRICS)
                        .sourceMetrics(EMPTY_SOURCE_METRICS)
                        .targetMetrics(EMPTY_TARGET_METRICS)
                        .build())
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionMetricsResponse expected =
                RetrieveConnectionMetricsResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionMetrics(TestConstants.Metrics.CONNECTION_METRICS)
                        .sourceMetrics(TestConstants.Metrics.SOURCE_METRICS1)
                        .targetMetrics(TestConstants.Metrics.TARGET_METRICS1)
                        .build();

        final RetrieveConnectionMetricsResponse actual =
                RetrieveConnectionMetricsResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = RetrieveConnectionMetricsResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                .connectionMetrics(TestConstants.Metrics.CONNECTION_METRICS)
                .sourceMetrics(TestConstants.Metrics.SOURCE_METRICS1)
                .targetMetrics(TestConstants.Metrics.TARGET_METRICS1)
                .build().toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.newPointer("/metrics");

        final RetrieveConnectionMetricsResponse underTest =
                RetrieveConnectionMetricsResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionMetrics(TestConstants.Metrics.CONNECTION_METRICS)
                        .sourceMetrics(TestConstants.Metrics.SOURCE_METRICS1)
                        .targetMetrics(TestConstants.Metrics.TARGET_METRICS1)
                        .build();

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
