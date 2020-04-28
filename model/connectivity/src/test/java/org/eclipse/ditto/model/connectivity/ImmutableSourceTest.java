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

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSource}.
 */
public final class ImmutableSourceTest {

    private static final AuthorizationContext AUTHORIZATION_CONTEXT =
            AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                    AuthorizationModelFactory.newAuthSubject("eclipse"),
                    AuthorizationModelFactory.newAuthSubject("ditto"));

    private static final Map<String, String> MAPPING;

    static {
        final Map<String, String> mapping = new HashMap<>();
        mapping.put("correlation-id", "{{ header:message-id }}");
        mapping.put("thing-id", "{{ header:device_id }}");
        mapping.put("eclipse", "ditto");
        MAPPING = Collections.unmodifiableMap(mapping);
    }

    private static final String AMQP_SOURCE1 = "amqp/source1";
    private static final String DITTO_MAPPING = "ditto-mapping";
    private static final String CUSTOM_MAPPING = "custom-mapping";
    private static final Source SOURCE_WITH_AUTH_CONTEXT =
            ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .consumerCount(2)
                    .index(0)
                    .address(AMQP_SOURCE1)
                    .headerMapping(ConnectivityModelFactory.newHeaderMapping(MAPPING))
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPING, CUSTOM_MAPPING))
                    .replyTarget(ImmutableReplyTargetTest.REPLY_TARGET)
                    .build();

    private static final JsonObject SOURCE_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add(AMQP_SOURCE1).build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .set(Source.JsonFields.HEADER_MAPPING,
                    JsonFactory.newObjectBuilder().setAll(MAPPING.entrySet().stream()
                            .map(e -> JsonFactory.newField(JsonFactory.newKey(e.getKey()), JsonValue.of(e.getValue())))
                            .collect(Collectors.toList())).build())
            .set(Source.JsonFields.PAYLOAD_MAPPING, JsonArray.of(DITTO_MAPPING, CUSTOM_MAPPING))
            .build();

    private static final JsonObject SOURCE_JSON_WITH_AUTH_CONTEXT = SOURCE_JSON.toBuilder()
            .set(Source.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .set(Source.JsonFields.REPLY_TARGET, ImmutableReplyTargetTest.REPLY_TARGET_JSON)
            .set(Source.JsonFields.REPLY_TARGET_ENABLED, true)
            .build();

    private static final String MQTT_SOURCE1 = "mqtt/source1";
    private static final String MQTT_FILTER = "topic/{{ thing.id }}";

    private static final Enforcement ENFORCEMENT = ConnectivityModelFactory.newEnforcement("{{ topic }}", MQTT_FILTER);

    private static final Source MQTT_SOURCE = ConnectivityModelFactory
            .newSourceBuilder()
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .address(MQTT_SOURCE1)
            .enforcement(ENFORCEMENT)
            .consumerCount(2)
            .index(0)
            .qos(1)
            .build();

    private static final JsonObject MQTT_SOURCE_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add(MQTT_SOURCE1).build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .set(Source.JsonFields.QOS, 1)
            .set(Source.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .set(Source.JsonFields.ENFORCEMENT, JsonFactory.newObjectBuilder()
                    .set(Enforcement.JsonFields.INPUT, "{{ topic }}")
                    .set(Enforcement.JsonFields.FILTERS, JsonFactory.newArrayBuilder().add(MQTT_FILTER).build())
                    .build())
            .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSource.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSource.class,
                areImmutable(),
                provided(AuthorizationContext.class,
                        Enforcement.class,
                        HeaderMapping.class,
                        PayloadMapping.class,
                        ReplyTarget.class).areAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = SOURCE_WITH_AUTH_CONTEXT.toJson();

        assertThat(actual).isEqualTo(SOURCE_JSON_WITH_AUTH_CONTEXT);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Source actual = ImmutableSource.fromJson(SOURCE_JSON_WITH_AUTH_CONTEXT, 0);

        assertThat(actual).isEqualTo(SOURCE_WITH_AUTH_CONTEXT);
    }

    @Test
    public void mqttToJsonReturnsExpected() {
        final JsonObject actual = MQTT_SOURCE.toJson();

        assertThat(actual).isEqualTo(MQTT_SOURCE_JSON);
    }

    @Test
    public void mqttFromJsonReturnsExpected() {
        final Source actual = ImmutableSource.fromJson(MQTT_SOURCE_JSON, 0);

        assertThat(actual).isEqualTo(MQTT_SOURCE);
    }

    @Test
    public void addMappingToExistingSource() {
        final Source sourceWithMapping = new ImmutableSource.Builder(SOURCE_WITH_AUTH_CONTEXT)
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping("mapping"))
                .build();

        assertThat(sourceWithMapping.getPayloadMapping().getMappings()).containsExactly("mapping");
    }

}
