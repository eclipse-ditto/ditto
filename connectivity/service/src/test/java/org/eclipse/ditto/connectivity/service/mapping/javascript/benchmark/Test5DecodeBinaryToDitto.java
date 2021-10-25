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
package org.eclipse.ditto.connectivity.service.mapping.javascript.benchmark;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import akka.actor.ActorSystem;

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
        externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withBytes(bytes)
                .build();
    }

    @Override
    public MessageMapper getMessageMapper() {
        final ActorSystem actorSystem = ActorSystem.create("Test", CONFIG);
        final MessageMapper javaScriptRhinoMapperPlain =
                JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlain.configure(CONNECTION, CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("decode", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_PLAIN)
                        .build(),
                actorSystem
        );
        actorSystem.terminate();
        return javaScriptRhinoMapperPlain;
    }

    @Override
    public ExternalMessage getExternalMessage() {
        return externalMessage;
    }
}

