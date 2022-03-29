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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newClientStatus;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newSourceStatus;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newTargetStatus;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.RecoveryStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveConnectionStatusResponse}.
 */
public final class RetrieveConnectionStatusResponseTest {

    private static final Instant IN_CONNECTION_STATUS_SINCE = Instant.now();
    private static final List<ResourceStatus> clientStatus =
            Arrays.asList(
                    newClientStatus("client1", ConnectivityStatus.OPEN, RecoveryStatus.SUCCEEDED, "Client is connected",
                            IN_CONNECTION_STATUS_SINCE),
                    newClientStatus("client2", ConnectivityStatus.FAILED, RecoveryStatus.ONGOING,  "Client failed to connect.",
                            IN_CONNECTION_STATUS_SINCE.plusSeconds(3))
            );
    private static final List<ResourceStatus> sourceStatus =
            Arrays.asList(
                    newSourceStatus("client1", ConnectivityStatus.OPEN, "source1","open since ..."),
                    newSourceStatus("client1", ConnectivityStatus.FAILED, "source1","this consumer fails ..."),
                    newSourceStatus("client1", ConnectivityStatus.CLOSED, "source2","closed since 123")
            );
    private static final List<ResourceStatus> targetStatus =
            Arrays.asList(
                    newTargetStatus("client1", ConnectivityStatus.OPEN, "target1","open since ..."),
                    newTargetStatus("client1", ConnectivityStatus.FAILED, "target2","this publisher fails ..."),
                    newTargetStatus("client1", ConnectivityStatus.CLOSED, "target3", "closed since 123")
            );

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, RetrieveConnectionStatusResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .set(RetrieveConnectionStatusResponse.JsonFields.CONNECTION_STATUS, ConnectivityStatus.OPEN.getName())
            .set(RetrieveConnectionStatusResponse.JsonFields.LIVE_STATUS, ConnectivityStatus.CLOSED.getName())
            .set(RetrieveConnectionStatusResponse.JsonFields.RECOVERY_STATUS, RecoveryStatus.UNKNOWN.getName())
            .set(RetrieveConnectionStatusResponse.JsonFields.CONNECTED_SINCE, IN_CONNECTION_STATUS_SINCE.toString())
            .set(RetrieveConnectionStatusResponse.JsonFields.CLIENT_STATUS,
                    JsonFactory.newArrayBuilder()
                            .add(JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.CLIENT.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.OPEN.getName())
                                            .set(ResourceStatus.JsonFields.RECOVERY_STATUS, RecoveryStatus.SUCCEEDED.getName())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "Client is connected")
                                            .set(ResourceStatus.JsonFields.IN_STATE_SINCE,
                                                    IN_CONNECTION_STATUS_SINCE.toString())
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.CLIENT.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client2")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.FAILED.getName())
                                            .set(ResourceStatus.JsonFields.RECOVERY_STATUS, RecoveryStatus.ONGOING.getName())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "Client failed to connect.")
                                            .set(ResourceStatus.JsonFields.IN_STATE_SINCE,
                                                    IN_CONNECTION_STATUS_SINCE.plusSeconds(3).toString())
                                            .build()
                            ).build())
            .set(RetrieveConnectionStatusResponse.JsonFields.SOURCE_STATUS,
                    JsonFactory.newArrayBuilder()
                            .add(JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.SOURCE.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.ADDRESS, "source1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.OPEN.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "open since ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.SOURCE.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.ADDRESS, "source1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.FAILED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "this consumer fails ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.SOURCE.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.ADDRESS, "source2")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.CLOSED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "closed since 123")
                                            .build()
                            ).build())
            .set(RetrieveConnectionStatusResponse.JsonFields.TARGET_STATUS,
                    JsonFactory.newArrayBuilder()
                            .add(JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.TARGET.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.ADDRESS, "target1")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.OPEN.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "open since ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.TARGET.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.ADDRESS, "target2")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.FAILED.toString())
                                            .set(ResourceStatus.JsonFields.STATUS_DETAILS, "this publisher fails ...")
                                            .build(),
                                    JsonFactory.newObjectBuilder()
                                            .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.TARGET.getName())
                                            .set(ResourceStatus.JsonFields.CLIENT, "client1")
                                            .set(ResourceStatus.JsonFields.ADDRESS, "target3")
                                            .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.CLOSED.toString())
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
                provided(JsonObject.class, ConnectionId.class).isAlsoImmutable());
    }

    @Test
    public void retrieveInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() ->
                        RetrieveConnectionStatusResponse.getBuilder(null, DittoHeaders.empty())
                                .connectionStatus(ConnectivityStatus.OPEN)
                                .liveStatus(ConnectivityStatus.CLOSED)
                                .connectedSince(IN_CONNECTION_STATUS_SINCE)
                                .clientStatus(Collections.emptyList())
                                .sourceStatus(Collections.emptyList())
                                .targetStatus(Collections.emptyList())
                                .build())
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void retrieveInstanceWithNullConnectionStatus() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(null)
                        .liveStatus(ConnectivityStatus.CLOSED)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(Collections.emptyList())
                        .sourceStatus(Collections.emptyList())
                        .targetStatus(Collections.emptyList())
                        .build())
                .withMessage("The %s must not be null!", "Connection Status")
                .withNoCause();
    }

    @Test
    public void retrieveInstanceWithNullLiveStatus() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(null)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(Collections.emptyList())
                        .sourceStatus(Collections.emptyList())
                        .targetStatus(Collections.emptyList())
                        .build())
                .withMessage("The %s must not be null!", "Live Connection Status")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionStatusResponse expected =
                RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.CLOSED)
                        .recoveryStatus(RecoveryStatus.UNKNOWN)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(clientStatus)
                        .sourceStatus(sourceStatus)
                        .targetStatus(targetStatus)
                        .build();

        final RetrieveConnectionStatusResponse actual =
                RetrieveConnectionStatusResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.CLOSED)
                        .recoveryStatus(RecoveryStatus.UNKNOWN)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(clientStatus)
                        .sourceStatus(sourceStatus)
                        .targetStatus(targetStatus)
                        .build()
                        .toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void mergeMultipleStatuses() {
        final RetrieveConnectionStatusResponse expected =
                RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.CLOSED)
                        .recoveryStatus(RecoveryStatus.UNKNOWN)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(clientStatus)
                        .sourceStatus(sourceStatus)
                        .targetStatus(targetStatus)
                        .build();

        final List<ResourceStatus> statuses = Stream.of(clientStatus, sourceStatus, targetStatus)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        // merge in random order
        Collections.shuffle(statuses);

        RetrieveConnectionStatusResponse.Builder builder =
                RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.CLOSED)
                        .recoveryStatus(RecoveryStatus.UNKNOWN)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(Collections.emptyList())
                        .sourceStatus(Collections.emptyList())
                        .targetStatus(Collections.emptyList());
        for (final ResourceStatus resourceStatus : statuses) {
            builder = builder.withAddressStatus(resourceStatus);
        }

        final RetrieveConnectionStatusResponse actual = builder.build();

        assertThat((Object) actual.getConnectionStatus()).isEqualTo(expected.getConnectionStatus());
        assertThat(actual.getClientStatus()).containsAll(expected.getClientStatus());
        assertThat(actual.getSourceStatus()).containsAll(expected.getSourceStatus());
        assertThat(actual.getTargetStatus()).containsAll(expected.getTargetStatus());
    }

    @Test
    public void missingStatuses() {
        final Map<ResourceStatus.ResourceType, Integer> expectedMissingResources = new HashMap<>();
        expectedMissingResources.put(ResourceStatus.ResourceType.SOURCE, 1);
        expectedMissingResources.put(ResourceStatus.ResourceType.TARGET, 2);
        final RetrieveConnectionStatusResponse expected =
                RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.FAILED) // failed because missing resources are included
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(clientStatus)
                        .withMissingResources(expectedMissingResources, 1, true)
                        .build();

        assertThat(expected.getSourceStatus()).contains(ConnectivityModelFactory.newSourceStatus("unknown-client",
                ConnectivityStatus.FAILED,
                null,
                "The <source> failed to report its status within the timeout."
        ));
        assertThat(expected.getTargetStatus()).contains(ConnectivityModelFactory.newTargetStatus("unknown-client",
                ConnectivityStatus.FAILED,
                null,
                "The <target> failed to report its status within the timeout."
        ), ConnectivityModelFactory.newTargetStatus("unknown-client",
                ConnectivityStatus.FAILED,
                null,
                "The <target> failed to report its status within the timeout."
        ));
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath =
                JsonFactory.newPointer("/status");

        final RetrieveConnectionStatusResponse underTest =
                RetrieveConnectionStatusResponse.getBuilder(TestConstants.ID, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.CLOSED)
                        .connectedSince(IN_CONNECTION_STATUS_SINCE)
                        .clientStatus(Collections.emptyList())
                        .sourceStatus(Collections.emptyList())
                        .targetStatus(Collections.emptyList())
                        .build();

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
