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
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.CheckPermissions;
import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.ImmutablePermissionCheck;
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
 * Unit tests for {@link CheckPermissionsCommandAdapter}.
 * Verifies round-trip serialization/deserialization of {@link CheckPermissions} signals
 * over the topic path {@code _/_/policies/commands/checkPermissions}.
 */
public final class CheckPermissionsCommandAdapterTest implements ProtocolAdapterTest {

    private static final TopicPath TOPIC_PATH =
            ProtocolFactory.newTopicPath("_/_/policies/commands/checkPermissions");

    private static final ImmutablePermissionCheck THING_READ_CHECK = ImmutablePermissionCheck.of(
            "thing:/",
            "org.eclipse.ditto.test:myThing",
            List.of("READ"));

    private static final ImmutablePermissionCheck POLICY_READ_CHECK = ImmutablePermissionCheck.of(
            "policy:/",
            "org.eclipse.ditto.test:myPolicy",
            List.of("READ"));

    private static final Map<String, ImmutablePermissionCheck> PERMISSION_CHECKS;

    static {
        PERMISSION_CHECKS = new LinkedHashMap<>();
        PERMISSION_CHECKS.put("thingRead", THING_READ_CHECK);
        PERMISSION_CHECKS.put("policyRead", POLICY_READ_CHECK);
    }

    private CheckPermissionsCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = CheckPermissionsCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test
    public void toAdaptable() {
        final CheckPermissions command = CheckPermissions.of(PERMISSION_CHECKS, TestConstants.DITTO_HEADERS_V_2);

        final JsonObject expectedPayload = JsonObject.newBuilder()
                .set("thingRead", THING_READ_CHECK.toJson())
                .set("policyRead", POLICY_READ_CHECK.toJson())
                .build();
        final Adaptable expected = Adaptable.newBuilder(TOPIC_PATH)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(expectedPayload)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final Adaptable actual = underTest.toAdaptable(command, TopicPath.Channel.NONE);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromAdaptable() {
        final JsonObject payloadValue = JsonObject.newBuilder()
                .set("thingRead", THING_READ_CHECK.toJson())
                .set("policyRead", POLICY_READ_CHECK.toJson())
                .build();
        final Adaptable adaptable = Adaptable.newBuilder(TOPIC_PATH)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(payloadValue)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CheckPermissions expected = CheckPermissions.of(PERMISSION_CHECKS, TestConstants.DITTO_HEADERS_V_2);

        final CheckPermissions actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void roundTripToAdaptable() {
        final CheckPermissions command = CheckPermissions.of(PERMISSION_CHECKS, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptable = underTest.toAdaptable(command, TopicPath.Channel.NONE);
        final CheckPermissions roundTripped = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(roundTripped).isEqualTo(command);
    }

    @Test
    public void roundTripFromAdaptable() {
        final JsonObject payloadValue = JsonObject.newBuilder()
                .set("thingRead", THING_READ_CHECK.toJson())
                .set("policyRead", POLICY_READ_CHECK.toJson())
                .build();
        final Adaptable adaptable = Adaptable.newBuilder(TOPIC_PATH)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(payloadValue)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CheckPermissions command = underTest.fromAdaptable(adaptable);
        final Adaptable roundTripped = underTest.toAdaptable(command, TopicPath.Channel.NONE);

        assertWithExternalHeadersThat(roundTripped).isEqualTo(adaptable);
    }

    @Test
    public void topicPathHasPoliciesGroupAndNoneChannel() {
        final CheckPermissions command = CheckPermissions.of(PERMISSION_CHECKS, TestConstants.DITTO_HEADERS_V_2);
        final TopicPath topicPath = underTest.toTopicPath(command, TopicPath.Channel.NONE);

        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.POLICIES);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.NONE);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.CHECK_PERMISSIONS);
        assertThat(topicPath.isWildcardTopic()).isTrue();
    }

    @Test
    public void emptyPermissionChecksRoundTrips() {
        final CheckPermissions command = CheckPermissions.of(Map.of(), TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptable = underTest.toAdaptable(command, TopicPath.Channel.NONE);
        final CheckPermissions roundTripped = underTest.fromAdaptable(adaptable);

        assertThat(roundTripped.getPermissionChecks()).isEmpty();
    }

    @Test
    public void adapterMetadata() {
        assertThat(underTest.getGroups()).containsExactly(TopicPath.Group.POLICIES);
        assertThat(underTest.getChannels()).containsExactly(TopicPath.Channel.NONE);
        assertThat(underTest.getCriteria()).containsExactly(TopicPath.Criterion.COMMANDS);
        assertThat(underTest.getActions()).containsExactly(TopicPath.Action.CHECK_PERMISSIONS);
        assertThat(underTest.isForResponses()).isFalse();
        assertThat(underTest.supportsWildcardTopics()).isTrue();
    }
}
