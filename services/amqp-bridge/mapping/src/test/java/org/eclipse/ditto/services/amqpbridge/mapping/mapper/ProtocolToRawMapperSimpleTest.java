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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * TODO doc
 *
 * Sorry, not really a test yet - class was used in order to manually test mapping functionality.
 */
//@RunWith(Parameterized.class)
public class ProtocolToRawMapperSimpleTest {

    private static final String CONTENT_TYPE = "text/plain";

    private static final String MAPPING_TEMPLATE =
            "ditto_protocolJson.topic = 'org.eclipse.ditto/foo-bar/things/twin/commands/create';" +
            "ditto_protocolJson.path = '/';" +
            "ditto_protocolJson.headers = {};" +
            "ditto_protocolJson.headers['correlation-id'] = ditto_mappingHeaders['correlation-id'];" +
            "ditto_protocolJson.value = ditto_mappingString;"
    ;

    private static final String PAYLOAD_STRING = "hello!";

    private static PayloadMapper javaScriptRhinoMapper;

//    @Parameterized.Parameters
//    public static List<Object[]> data() {
//        return Arrays.asList(new Object[20][0]);
//    }

    @BeforeClass
    public static void setup() {
        javaScriptRhinoMapper = PayloadMappers.createJavaScriptRhinoMapper(
                PayloadMappers
                        .createJavaScriptMapperOptionsBuilder()
                        .incomingMappingScript(MAPPING_TEMPLATE)
                        .build());
    }

    @Test
    public void testRhinoMapper() {
        System.out.println("\nRhino");

        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "4711-foobar");
        final PayloadMapperMessage message = new ImmutablePayloadMapperMessage(CONTENT_TYPE, null, "huhu!", headers);

        final long startTs = System.nanoTime();
        final Adaptable adaptable = javaScriptRhinoMapper.mapIncoming(message);
        System.out.println(adaptable);
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }
}
