package org.eclipse.ditto.connectivity.service.mapping;
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

import org.apache.kafka.common.protocol.types.Field;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.eclipse.ditto.connectivity.service.mapping.AbstractMessageMapper.extractPayloadAsString;
import static org.junit.Assert.assertEquals;

public class CloudEventsMapperTest {

    private static final ThingId THING_ID = ThingId.of("thing:id");
    private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();

    String payload = """
            {
             "specversion": "1.0",  "id":"3212e","source":"http:somesite.com","type":"com.site.com"
             }
            """;

    String incompletePayload = """
            { "id":"3212e", "source":"http:somesite.com","type":"com.site.com"}"
            """;
    String testPayload = """
            {"specversion": "1.0", "id":"3212e", "source":"http:somesite.com","type":"com.site.com",
            "data":{"topic":"my.sensors/sensor01/things/twin/commands/modify",
            "path":"/","value":
            {"thingId": "my.sensors:sensor01","policyId": "my.test:policy", 
            "attributes": {"manufacturer": "Well known sensors producer","serial number": "100","location": "Ground floor" },
            "features": {"measurements":{"properties":{"temperature": 100,"humidity": 0}}}}}} 
            """;
    String data = """
            {"topic":"my.sensors/sensor01/things/twin/commands/modify",
            "path":"/","value":
            {"thingId": "my.sensors:sensor01","policyId": "my.test:policy",
            "attributes": {"manufacturer": "Well known sensors producer","serial number": "100","location": "Ground floor" },
            "features": {"measurements":{"properties":{"temperature": 100,"humidity": 0}}}}}
            """;


    String base64payload = """
    {"specversion": "1.0" , "id":"3212e", "source":"http:somesite.com","type":"com.site.com", "data_base64":"ewogICJ0b3BpYyI6Im15LnNlbnNvcnMvc2Vuc29yMDEvdGhpbmdzL3R3aW4vY29tbWFuZHMvbW9kaWZ5IiwKICAicGF0aCI6Ii8iLAogICJ2YWx1ZSI6ewogICAgICAidGhpbmdJZCI6ICJteS5zZW5zb3JzOnNlbnNvcjAxIiwKICAgICAgInBvbGljeUlkIjogIm15LnRlc3Q6cG9saWN5IiwKICAgICAgImF0dHJpYnV0ZXMiOiB7CiAgICAgICAgICAibWFudWZhY3R1cmVyIjogIldlbGwga25vd24gc2Vuc29ycyBwcm9kdWNlciIsCiAgICAgICAgICAgICJzZXJpYWwgbnVtYmVyIjogIjEwMCIsIAogICAgICAgICAgICAibG9jYXRpb24iOiAiR3JvdW5kIGZsb29yIiB9LAogICAgICAgICAgICAiZmVhdHVyZXMiOiB7CiAgICAgICAgICAgICAgIm1lYXN1cmVtZW50cyI6IAogICAgICAgICAgICAgICB7InByb3BlcnRpZXMiOiAKICAgICAgICAgICAgICAgeyJ0ZW1wZXJhdHVyZSI6IDEwMCwKICAgICAgICAgICAgICAgICJodW1pZGl0eSI6IDB9fX19fQ=="}
    """;
    String data_base64 = Base64.getEncoder().encodeToString(data.getBytes());
    private CloudEventsMapper underTest;

    @Before
    public void setUp() {
        underTest = new CloudEventsMapper();
    }

    @Test
    public void textPayloadMessage() {
        ExternalMessage textMessage = textMessageBuilder(testPayload);
        Adaptable expectedAdaptable = DittoJsonException.wrapJsonRuntimeException(() -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(data)));
        List<Adaptable> expectedMap = singletonList(ProtocolFactory.newAdaptableBuilder(expectedAdaptable).build());
        assertEquals(expectedMap, underTest.map(textMessage));
    }

    @Test
    public void base64PayloadMessage() {
        ExternalMessage message = textMessageBuilder(base64payload);
        String base64 = data_base64.replace("\"", "");
        byte[] decodedBytes = Base64.getDecoder().decode(base64);
        String decodedString = new String(decodedBytes);

        Adaptable expectedAdaptable = DittoJsonException.wrapJsonRuntimeException(() -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(decodedString)));
        List<Adaptable> expectedMap = singletonList(ProtocolFactory.newAdaptableBuilder(expectedAdaptable).build());
        assertEquals(expectedMap, underTest.map(message));
    }

    @Test
    public void bytePayloadMapping() {
        ExternalMessage byteMessage = ExternalMessageFactory.newExternalMessageBuilder(Map.of(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)).withBytes(testPayload.getBytes(StandardCharsets.UTF_8)).build();

        Adaptable expectedAdaptable = DittoJsonException.wrapJsonRuntimeException(() -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(data.getBytes(StandardCharsets.UTF_8))));
        List<Adaptable> expectedMap = singletonList(ProtocolFactory.newAdaptableBuilder(expectedAdaptable).build());
        assertEquals(expectedMap, underTest.map(byteMessage));
    }

    @Test
    public void validatePayloadTest() {
        Boolean expected = true;
        Boolean actual = underTest.validatePayload(payload);
        assertEquals(expected, actual);
    }

    @Test
    public void failedValidation() {
        Boolean expected = false;
        Boolean actual = underTest.validatePayload(incompletePayload);
        assertEquals(expected, actual);

    }

    private ExternalMessage textMessageBuilder(String textPayload) {
        ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(Map.of(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)).withText(textPayload).build();
        return message;
    }
}
