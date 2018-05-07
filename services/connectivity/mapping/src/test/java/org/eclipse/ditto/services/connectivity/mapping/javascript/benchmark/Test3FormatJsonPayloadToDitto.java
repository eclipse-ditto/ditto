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
public class Test3FormatJsonPayloadToDitto implements MapToDittoProtocolScenario {

    private static final String CONTENT_TYPE = "application/json";

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
                    "    let namespace = \"org.eclipse.ditto\";\n" +
                    "    let id = \"jmh-test\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"/\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    const input = {\n" +
                    "    thingId: 'RC-CRaaSCar',\n" +
                    "    acl: {\n" +
                    "      manufacturer: {\n" +
                    "        READ: true,\n" +
                    "        WRITE: true,\n" +
                    "        ADMINISTRATE: true\n" +
                    "      },\n" +
                    "      driver1: {\n" +
                    "        READ: true,\n" +
                    "        WRITE: false,\n" +
                    "        ADMINISTRATE: false\n" +
                    "      }\n" +
                    "    },\n" +
                    "    attributes: {\n" +
                    "      manufacturer: 'myManufacturer',\n" +
                    "      model: 'myModel',\n" +
                    "      yearOfConstruction: 2000,\n" +
                    "      serialNumber: '123456789ABC'\n" +
                    "    },\n" +
                    "    features: {\n" +
                    "      XDK: {\n" +
                    "        properties: {\n" +
                    "          acceleration: {\n" +
                    "            x: -1,\n" +
                    "            y: 0,\n" +
                    "            z: 0\n" +
                    "          },\n" +
                    "          gyroscope: {\n" +
                    "            x: 0,\n" +
                    "            y: 0,\n" +
                    "            z: 0\n" +
                    "          },\n" +
                    "          magnetometer: {\n" +
                    "            x: 0,\n" +
                    "            y: 0,\n" +
                    "            z: 0\n" +
                    "          },\n" +
                    "          environmental: {\n" +
                    "            temperature: 25.3,\n" +
                    "            humidity: 63.4,\n" +
                    "            pressure: 982.5\n" +
                    "          },\n" +
                    "          light: 598.4,\n" +
                    "          lamps: {\n" +
                    "            yellow: true,\n" +
                    "            orange: true,\n" +
                    "            red: false\n" +
                    "          }\n" +
                    "        }\n" +
                    "      },\n" +
                    "      secondary: {\n" +
                    "        properties: {\n" +
                    "          velocity: 15.4,\n" +
                    "          state: 'moving',\n" +
                    "          direction: 54.4\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "    };\n" +
                    "    let value = JSON.stringify(input);\n" +
                    "    // ###\n" +
                    "\n" +
                    "    return Ditto.buildDittoProtocolMsg(\n" +
                    "        namespace,\n" +
                    "        id,\n" +
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

    public Test3FormatJsonPayloadToDitto() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE);
        externalMessage = ConnectivityModelFactory.newExternalMessageBuilder(headers)
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

