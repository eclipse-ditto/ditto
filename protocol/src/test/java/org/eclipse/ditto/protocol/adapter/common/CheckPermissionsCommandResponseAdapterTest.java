/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.base.api.common.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link CheckPermissionsCommandResponseAdapter}.
 * Verifies round-trip serialization/deserialization of {@link CheckPermissionsResponse} signals
 * over the topic path {@code _/_/common/commands/checkPermissions}.
 */
public final class CheckPermissionsCommandResponseAdapterTest implements ProtocolAdapterTest {

    private static final TopicPath TOPIC_PATH =
            ProtocolFactory.newTopicPath("_/_/common/commands/checkPermissions");

    private static final Map<String, Boolean> PERMISSION_RESULTS;

    static {
        PERMISSION_RESULTS = new LinkedHashMap<>();
        PERMISSION_RESULTS.put("thingRead", true);
        PERMISSION_RESULTS.put("policyRead", false);
    }

    private CheckPermissionsCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = CheckPermissionsCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test
    public void toAdaptable() {
        final CheckPermissionsResponse response =
                CheckPermissionsResponse.of(PERMISSION_RESULTS, TestConstants.DITTO_HEADERS_V_2);

        final JsonObject expectedPayload = JsonObject.newBuilder()
                .set("thingRead", true)
                .set("policyRead", false)
                .build();
        final Adaptable expected = Adaptable.newBuilder(TOPIC_PATH)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withStatus(HttpStatus.OK)
                        .withValue(expectedPayload)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final Adaptable actual = underTest.toAdaptable(response, TopicPath.Channel.NONE);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromAdaptable() {
        final JsonObject payloadValue = JsonObject.newBuilder()
                .set("thingRead", true)
                .set("policyRead", false)
                .build();
        final Adaptable adaptable = Adaptable.newBuilder(TOPIC_PATH)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withStatus(HttpStatus.OK)
                        .withValue(payloadValue)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CheckPermissionsResponse expected =
                CheckPermissionsResponse.of(PERMISSION_RESULTS, TestConstants.DITTO_HEADERS_V_2);

        final CheckPermissionsResponse actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void roundTripToAdaptable() {
        final CheckPermissionsResponse response =
                CheckPermissionsResponse.of(PERMISSION_RESULTS, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptable = underTest.toAdaptable(response, TopicPath.Channel.NONE);
        final CheckPermissionsResponse roundTripped = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(roundTripped).isEqualTo(response);
    }

    @Test
    public void roundTripFromAdaptable() {
        final JsonObject payloadValue = JsonObject.newBuilder()
                .set("thingRead", true)
                .set("policyRead", false)
                .build();
        final Adaptable adaptable = Adaptable.newBuilder(TOPIC_PATH)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withStatus(HttpStatus.OK)
                        .withValue(payloadValue)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CheckPermissionsResponse response = underTest.fromAdaptable(adaptable);
        final Adaptable roundTripped = underTest.toAdaptable(response, TopicPath.Channel.NONE);

        assertWithExternalHeadersThat(roundTripped).isEqualTo(adaptable);
    }

    @Test
    public void allGrantedRoundTrips() {
        final Map<String, Boolean> allGranted = Map.of("check1", true, "check2", true);
        final CheckPermissionsResponse response =
                CheckPermissionsResponse.of(allGranted, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptable = underTest.toAdaptable(response, TopicPath.Channel.NONE);
        final CheckPermissionsResponse roundTripped = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(roundTripped).isEqualTo(response);
    }

    @Test
    public void emptyResultsRoundTrips() {
        final CheckPermissionsResponse response =
                CheckPermissionsResponse.of(Map.of(), TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptable = underTest.toAdaptable(response, TopicPath.Channel.NONE);
        final CheckPermissionsResponse roundTripped = underTest.fromAdaptable(adaptable);

        assertThat(roundTripped.getEntity(org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2).asObject())
                .isEqualTo(JsonObject.empty());
    }

    @Test
    public void adapterMetadata() {
        assertThat(underTest.getGroups()).containsExactly(TopicPath.Group.COMMON);
        assertThat(underTest.getChannels()).containsExactly(TopicPath.Channel.NONE);
        assertThat(underTest.getCriteria()).containsExactly(TopicPath.Criterion.COMMANDS);
        assertThat(underTest.getActions()).containsExactly(TopicPath.Action.CHECK_PERMISSIONS);
        assertThat(underTest.isForResponses()).isTrue();
        assertThat(underTest.supportsWildcardTopics()).isTrue();
    }
}
