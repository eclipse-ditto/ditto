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
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newClientStatus;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newSourceStatus;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTargetStatus;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveConnectionStatusResponse}.
 */
public final class RetrieveConnectionStatusResponseTest {

    private static final Instant IN_CONNECTION_STATUS_SINCE = Instant.now();
    private static List<ResourceStatus> clientStatus =
            Arrays.asList(
                    newClientStatus("client1", ConnectionStatus.OPEN, "Client is connected",
                            IN_CONNECTION_STATUS_SINCE),
                    newClientStatus("client2", ConnectionStatus.FAILED, "Client failed to connect.",
                            IN_CONNECTION_STATUS_SINCE)
            );
    private static List<ResourceStatus> sourceStatus =
            Arrays.asList(
                    newSourceStatus("source1", ConnectionStatus.OPEN, "open since ..."),
                    newSourceStatus("source1", ConnectionStatus.FAILED, "this consumer fails ..."),
                    newSourceStatus("source2", ConnectionStatus.CLOSED, "closed since 123")
            );
    private static List<ResourceStatus> targetStatus =
            Arrays.asList(
                    newTargetStatus("target1", ConnectionStatus.OPEN, "open since ..."),
                    newTargetStatus("target2", ConnectionStatus.FAILED, "this publisher fails ..."),
                    newTargetStatus("target3", ConnectionStatus.CLOSED, "closed since 123")
            );

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, RetrieveConnectionStatusResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, TestConstants.ID)
            .set(RetrieveConnectionStatusResponse.JsonFields.CONNECTION_STATUS, ConnectionStatus.OPEN.getName())
            .set(RetrieveConnectionStatusResponse.JsonFields.LIVE_STATUS, ConnectionStatus.CLOSED.getName())
            .set(RetrieveConnectionStatusResponse.JsonFields.CLIENT_STATUS,
                    JsonFactory.newArrayBuilder()
                            .add(JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "client1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.OPEN.getName())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "Client is connected")
                                            .set(ResourceStatus.JsonFields.IN_STATE_SINCE,
                                                    IN_CONNECTION_STATUS_SINCE.toString())
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "client2")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.FAILED.getName())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "Client failed to connect.")
                                            .set(ResourceStatus.JsonFields.IN_STATE_SINCE,
                                                    IN_CONNECTION_STATUS_SINCE.toString())
                                            .build()
                            ).build())
            .set(RetrieveConnectionStatusResponse.JsonFields.SOURCE_STATUS,
                    JsonFactory.newArrayBuilder()
                            .add(JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "source1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.OPEN.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "open since ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "source1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.FAILED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "this consumer fails ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "source2")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.CLOSED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "closed since 123")
                                            .build()
                            ).build())
            .set(RetrieveConnectionStatusResponse.JsonFields.TARGET_STATUS,
                    JsonFactory.newArrayBuilder()
                            .add(JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "target1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.OPEN.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "open since ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "target2")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.FAILED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "this publisher fails ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.ADDRESS, "target3")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectionStatus.CLOSED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "closed since 123")
                                            .build()
                            ).build())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveConnectionStatusResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnectionStatusResponse.class, areImmutable(),
                provided(ConnectionStatus.class).isAlsoImmutable(),
                assumingFields("sourceStatus", "targetStatus", "clientStatus"
                ).areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void retrieveInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionStatusResponse.of(null,
                        ConnectionStatus.OPEN,
                        ConnectionStatus.CLOSED,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void retrieveInstanceWithNullConnectionStatus() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionStatusResponse.of(TestConstants.ID, null, ConnectionStatus.CLOSED,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection Status")
                .withNoCause();
    }

    @Test
    public void retrieveInstanceWithNullLiveStatus() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Live Connection Status")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionStatusResponse expected =
                RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, ConnectionStatus.CLOSED,
                        clientStatus,
                        sourceStatus,
                        targetStatus,
                        DittoHeaders.empty());

        final RetrieveConnectionStatusResponse actual =
                RetrieveConnectionStatusResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, ConnectionStatus.CLOSED,
                        clientStatus,
                        sourceStatus,
                        targetStatus,
                        DittoHeaders.empty())
                        .toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void mergeMultipleStatuses() {
        final RetrieveConnectionStatusResponse expected =
                RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, ConnectionStatus.CLOSED,
                        clientStatus,
                        sourceStatus,
                        targetStatus,
                        DittoHeaders.empty());


        final RetrieveConnectionStatusResponse empty =
                RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, ConnectionStatus.CLOSED,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        DittoHeaders.empty());

        final List<ResourceStatus> statuses = Stream.of(clientStatus, sourceStatus, targetStatus)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        // merge in random order
        Collections.shuffle(statuses);

        RetrieveConnectionStatusResponse actual =
                RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, ConnectionStatus.CLOSED,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        DittoHeaders.empty());
        for (final ResourceStatus resourceStatus : statuses) {
            actual = actual.withAddressStatus(resourceStatus);
        }

        assertThat((Object) actual.getConnectionStatus()).isEqualTo(expected.getConnectionStatus());
        assertThat(actual.getClientStatus()).containsAll(expected.getClientStatus());
        assertThat(actual.getSourceStatus()).containsAll(expected.getSourceStatus());
        assertThat(actual.getTargetStatus()).containsAll(expected.getTargetStatus());
    }
}
