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
public class Test1DecodeBinaryPayloadToDitto implements MapToDittoProtocolScenario {

    private static final String MAPPING_BYTES = "27408B";
    private static final String CONTENT_TYPE = "application/octet-stream";

    private static final String MAPPING_INCOMING_PLAIN =
            "function mapToDittoProtocolMsg(\n" +
                    "    headers,\n" +
                    "    textPayload,\n" +
                    "    bytePayload,\n" +
                    "    contentType\n" +
                    ") {\n" +
                    "\n" +
                    "    // ###\n" +
                    "    // Insert your mapping logic here\n" +
                    "    function intFromBytes(arrayBuffer){\n" +
                    "       let byteBuf = Ditto.asByteBuffer(arrayBuffer);\n" +
                    "       return parseInt(byteBuf.toHex(), 16);\n" +
                    "    };\n" +
                    "    let namespace = \"org.eclipse.ditto\";\n" +
                    "    let name = \"jmh-test\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"/attributes/foo\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let theBytes = intFromBytes(bytePayload);\n" +
                    "    let value = {\n" +
                    "       a: theBytes & 0b1111,\n" +
                    "       b: (theBytes >>> 4) & 0b1111,\n" +
                    "       c: 99\n" +
                    "    };\n" +
                    "    // ###\n" +
                    "\n" +
                    "    return Ditto.buildDittoProtocolMsg(\n" +
                    "        namespace,\n" +
                    "        name,\n" +
                    "        group,\n" +
                    "        channel,\n" +
                    "        criterion,\n" +
                    "        action,\n" +
                    "        path,\n" +
                    "        dittoHeaders,\n" +
                    "        value\n" +
                    "    );\n" +
                    "}";

    private final ExternalMessage externalMessage;

    public Test1DecodeBinaryPayloadToDitto() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", correlationId);
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
                        .createJavaScriptMessageMapperConfigurationBuilder("binary", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_PLAIN)
                        .loadBytebufferJS(true)
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

