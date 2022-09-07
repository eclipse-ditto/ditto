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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableTarget}.
 */
public final class ImmutableTargetTest {

    private static final String ADDRESS = "amqp/target1";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT =
            AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                    AuthorizationModelFactory.newAuthSubject("eclipse"),
                    AuthorizationModelFactory.newAuthSubject("ditto"));
    private static final String CUSTOM_MAPPING = "custom-mapping";
    private static final String DITTO_MAPPING = "ditto-mapping";
    private static final AcknowledgementLabel ACKNOWLEDGEMENT_LABEL = AcknowledgementLabel.of("custom-ack");

    private static final Target TARGET = ConnectivityModelFactory
            .newTargetBuilder()
            .address(ADDRESS)
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .topics(Topic.TWIN_EVENTS)
            .issuedAcknowledgementLabel(ACKNOWLEDGEMENT_LABEL)
            .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPING, CUSTOM_MAPPING))
            .build();

    private static final JsonObject TARGET_JSON = JsonObject.newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder().add(Topic.TWIN_EVENTS.getName()).build())
            .set(Target.JsonFields.ADDRESS, ADDRESS)
            .set(Target.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .set(Target.JsonFields.ISSUED_ACKNOWLEDGEMENT_LABEL, "custom-ack")
            .set(Target.JsonFields.HEADER_MAPPING, JsonObject.empty())
            .set(Target.JsonFields.PAYLOAD_MAPPING, JsonArray.of(DITTO_MAPPING, CUSTOM_MAPPING))
            .build();

    private static final String MQTT_ADDRESS = "mqtt/target1";

    private static final Target MQTT_TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address(MQTT_ADDRESS)
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .qos(1)
            .topics(Topic.TWIN_EVENTS)
            .build();

    private static final JsonObject MQTT_TARGET_JSON = JsonObject.newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder().add(Topic.TWIN_EVENTS.getName()).build())
            .set(Target.JsonFields.ADDRESS, MQTT_ADDRESS)
            .set(Target.JsonFields.QOS, 1)
            .set(Target.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .set(Target.JsonFields.HEADER_MAPPING, JsonObject.empty())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableTarget.class, areImmutable(),
                provided(AuthorizationContext.class,
                        FilteredTopic.class,
                        HeaderMapping.class,
                        AcknowledgementLabel.class,
                        PayloadMapping.class).areAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = TARGET.toJson();

        assertThat(actual).isEqualTo(TARGET_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Target actual = ImmutableTarget.fromJson(TARGET_JSON);

        assertThat(actual).isEqualTo(TARGET);
    }

    @Test
    public void mqttToJsonReturnsExpected() {
        final JsonObject actual = MQTT_TARGET.toJson();

        assertThat(actual).isEqualTo(MQTT_TARGET_JSON);
    }

    @Test
    public void mqttFromJsonReturnsExpected() {
        final Target actual = ImmutableTarget.fromJson(MQTT_TARGET_JSON);

        assertThat(actual).isEqualTo(MQTT_TARGET);
    }

}
