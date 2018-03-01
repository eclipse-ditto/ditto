/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * TODO doc
 *
 * Sorry, not really a test yet - class was used in order to manually test mapping functionality.
 */
//@RunWith(Parameterized.class)
public class ProtocolToRawMapperBytesTest {

    private static final String CONTENT_TYPE = "application/octet-stream";

    private static final String MAPPING_TEMPLATE =
            "ditto_protocolJson.topic = 'org.eclipse.ditto/foo-bar/things/twin/commands/create';" +
            "ditto_protocolJson.path = '/';" +
            "ditto_protocolJson.headers = {};" +
            "ditto_protocolJson.headers['correlation-id'] = ditto_mappingHeaders['correlation-id'];" +
            "ditto_protocolJson.value = String.fromCharCode.apply(String, ditto_mappingByteArray);"
    ;

    private static final ByteBuffer PAYLOAD_BYTEBUFFER = ByteBuffer.wrap("hello binary!".getBytes(StandardCharsets.UTF_8));

    private static MessageMapper javaScriptRhinoMapper;

//    @Parameterized.Parameters
//    public static List<Object[]> data() {
//        return Arrays.asList(new Object[20][0]);
//    }

    @BeforeClass
    public static void setup() {
        javaScriptRhinoMapper = MessageMappers.createJavaScriptMessageMapper();
        MessageMapperConfiguration configuration = JavaScriptMessageMapperFactory
                .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                .contentType(CONTENT_TYPE)
                .incomingMappingScript(MAPPING_TEMPLATE)
                .loadMustacheJS(true)
                .build();
        javaScriptRhinoMapper.configureWithValidation(configuration);
    }

    @Test
    public void testRhinoMapper() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "4711-foobar");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE);
        final ExternalMessage message = AmqpBridgeModelFactory.newExternalMessageBuilder(headers, ExternalMessage
                .MessageType.EVENT).withBytes(PAYLOAD_BYTEBUFFER).build();

        final long startTs = System.nanoTime();
        final Adaptable adaptable = javaScriptRhinoMapper.map(message);
        System.out.println(adaptable);
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }
}
