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
package org.eclipse.ditto.services.connectivity.mapping.javascript.benchmark;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperFactory;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class Test5DecodeBinaryToDitto implements MapToDittoProtocolScenario {

    private static final String MAPPING_BYTES = "09EF03F72A";
    private static final String CONTENT_TYPE = "application/octet-stream";

    private static final String MAPPING_INCOMING_PLAIN =
            "function mapToDittoProtocolMsg(\n" +
                    "    headers,\n" +
                    "    textPayload,\n" +
                    "    bytePayload,\n" +
                    "    contentType\n" +
                    ") {\n" +
                    "\n" +
                    "    let view = new DataView(bytePayload);\n" +
                    "    \n" +
                    "    let value = {};\n" +
                    "    value.temperature = {};\n" +
                    "    value.temperature.properties = {};\n" +
                    "    value.temperature.properties.value = view.getInt16(0) / 100.0;\n" +
                    "    \n" +
                    "    value.pressure = {};\n" +
                    "    value.pressure.properties = {};\n" +
                    "    value.pressure.properties.value = view.getInt16(2);\n" +
                    "    \n" +
                    "    value.humidity = {};\n" +
                    "    value.humidity.properties = {};\n" +
                    "    value.humidity.properties.value = view.getUint8(4);\n" +
                    "\n" +
                    "    return Ditto.buildDittoProtocolMsg(\n" +
                    "        'org.eclipse.ditto', // in this example always the same\n" +
                    "        headers['device_id'], // Eclipse Hono sets the authenticated device_id as AMQP 1.0 header\n" +
                    "        'things', // we deal with a Thing\n" +
                    "        'twin', // we want to update the twin\n" +
                    "        'commands', // we want to create a command to update a twin\n" +
                    "        'modify', // modify the twin\n" +
                    "        '/features', // modify all features at once\n" +
                    "        headers, // pass through the headers from AMQP 1.0\n" +
                    "        value\n" +
                    "    );\n" +
                    "}";

    private final ExternalMessage externalMessage;

    public Test5DecodeBinaryToDitto() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", correlationId);
        headers.put("device_id", "jmh-test");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE);
        final BigInteger bigInteger = new BigInteger(MAPPING_BYTES, 16);
        System.out.println(bigInteger);
        final byte[] bytes = bigInteger.toByteArray();
        System.out.println("bytes length: " + bytes.length);
        externalMessage = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                .withBytes(bytes)
                .build();
    }

    @Override
    public MessageMapper getMessageMapper() {
        final MessageMapper javaScriptRhinoMapperPlain = MessageMappers.createJavaScriptMessageMapper();
        javaScriptRhinoMapperPlain.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                        .contentType(CONTENT_TYPE)
                        .incomingScript(MAPPING_INCOMING_PLAIN)
                        .build()
        );
        return javaScriptRhinoMapperPlain;
    }

    @Override
    public ExternalMessage getExternalMessage() {
        return externalMessage;
    }
}

