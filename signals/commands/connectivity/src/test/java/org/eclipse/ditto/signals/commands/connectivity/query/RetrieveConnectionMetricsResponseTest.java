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
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.connectivity.TestConstants.ID;
import static org.eclipse.ditto.signals.commands.connectivity.TestConstants.Metrics.mergeMeasurements;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashMap;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MetricType;
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
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, ID)
            .set(JsonFields.JSON_CONNECTION_METRICS, Metrics.Json.CONNECTION_METRICS_JSON)
            .set(JsonFields.JSON_SOURCE_METRICS, Metrics.SOURCE_METRICS1.toJson())
            .set(JsonFields.JSON_TARGET_METRICS, Metrics.TARGET_METRICS1.toJson())
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
                provided(ConnectionMetrics.class, SourceMetrics.class, TargetMetrics.class).isAlsoImmutable());
    }

    @Test
    public void retrieveInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> RetrieveConnectionMetricsResponse.of(null, METRICS, EMPTY_SOURCE_METRICS,
                                EMPTY_TARGET_METRICS, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionMetricsResponse expected =
                RetrieveConnectionMetricsResponse.of(ID,
                        Metrics.CONNECTION_METRICS,
                        Metrics.SOURCE_METRICS1,
                        Metrics.TARGET_METRICS1,
                        DittoHeaders.empty());

        final RetrieveConnectionMetricsResponse actual =
                RetrieveConnectionMetricsResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveConnectionMetricsResponse.of(ID,
                        Metrics.CONNECTION_METRICS,
                        Metrics.SOURCE_METRICS1,
                        Metrics.TARGET_METRICS1,
                        DittoHeaders.empty()).toJson();

        System.out.println(actual);

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void mergeRetrieveConnectionMetricsResponses() {

        final RetrieveConnectionMetricsResponse merged = Metrics.METRICS_RESPONSE1.mergeWith(Metrics.METRICS_RESPONSE2);

        assertThat(merged.getConnectionId()).isEqualTo(ID);

        // check overall sum of connection metrics
        assertThat(merged.getConnectionMetrics().getInboundMetrics().getMeasurements())
                .contains(mergeMeasurements(MetricType.CONSUMED, true, Metrics.INBOUND, 4));
        assertThat(merged.getConnectionMetrics().getInboundMetrics().getMeasurements())
                .contains(mergeMeasurements(MetricType.MAPPED, true, Metrics.MAPPING, 4));

        assertThat(merged.getConnectionMetrics().getOutboundMetrics().getMeasurements())
                .contains(mergeMeasurements(MetricType.PUBLISHED, true, Metrics.OUTBOUND, 4));
        assertThat(merged.getConnectionMetrics().getOutboundMetrics().getMeasurements())
                .contains(mergeMeasurements(MetricType.MAPPED, true, Metrics.MAPPING, 4));

        // check source metrics
        assertThat(merged.getSourceMetrics().getAddressMetrics()).containsKeys("source1", "source2", "source3");
        assertThat(merged.getSourceMetrics().getAddressMetrics().get("source1").getMeasurements())
                .contains(Metrics.INBOUND, Metrics.MAPPING);
        assertThat(merged.getSourceMetrics().getAddressMetrics().get("source2").getMeasurements())
                .contains(mergeMeasurements(MetricType.CONSUMED, true, Metrics.INBOUND,2),
                        mergeMeasurements(MetricType.MAPPED, true, Metrics.MAPPING, 2));
        assertThat(merged.getSourceMetrics().getAddressMetrics().get("source3").getMeasurements())
                .contains(Metrics.INBOUND, Metrics.MAPPING);

        // check target metrics
        assertThat(merged.getTargetMetrics().getAddressMetrics()).containsKeys("target1", "target2", "target3");
        assertThat(merged.getTargetMetrics().getAddressMetrics().get("target1").getMeasurements())
                .contains(Metrics.MAPPING, Metrics.OUTBOUND);
        assertThat(merged.getTargetMetrics().getAddressMetrics().get("target2").getMeasurements())
                .contains(mergeMeasurements(MetricType.MAPPED, true, Metrics.MAPPING, 2),
                        mergeMeasurements(MetricType.PUBLISHED, true, Metrics.OUTBOUND, 2));
        assertThat(merged.getTargetMetrics().getAddressMetrics().get("target3").getMeasurements())
                .contains(Metrics.MAPPING, Metrics.OUTBOUND);
    }

}
