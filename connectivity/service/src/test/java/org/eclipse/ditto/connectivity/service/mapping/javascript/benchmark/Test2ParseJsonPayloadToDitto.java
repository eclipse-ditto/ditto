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
public class Test2ParseJsonPayloadToDitto implements MapToDittoProtocolScenario {

    private static final String MAPPING_JSON = "{\n" +
            "    \"thingId\": \"RC-CRaaSCar\",\n" +
            "    \"acl\": {\n" +
            "        \"manufacturer\":{\n" +
            "            \"READ\":true,\n" +
            "            \"WRITE\":true,\n" +
            "            \"ADMINISTRATE\":true\n" +
            "        },\n" +
            "        \"driver1\":{\n" +
            "            \"READ\":true,\n" +
            "            \"WRITE\":false,\n" +
            "            \"ADMINISTRATE\":false\n" +
            "        }\n" +
            "    },\n" +
            "    \"attributes\": {\n" +
            "        \"manufacturer\": \"myManufacturer\",\n" +
            "        \"model\": \"myModel\",\n" +
            "        \"yearOfConstruction\": 2000,\n" +
            "        \"serialNumber\": \"123456789ABC\"\n" +
            "    },\n" +
            "    \"features\": {\n" +
            "        \"XDK\": {\n" +
            "            \"properties\": {\n" +
            "                \"acceleration\": {\n" +
            "                    \"x\": -1,\n" +
            "                    \"y\": 0,\n" +
            "                    \"z\": 0\n" +
            "                },\n" +
            "                \"gyroscope\": {\n" +
            "                    \"x\": 0,\n" +
            "                    \"y\": 0,\n" +
            "                    \"z\": 0\n" +
            "                },\n" +
            "                \"magnetometer\": {\n" +
            "                    \"x\": 0,\n" +
            "                    \"y\": 0,\n" +
            "                    \"z\": 0\n" +
            "                },\n" +
            "                \"environmental\": {\n" +
            "                    \"temperature\": 25.3,\n" +
            "                    \"humidity\": 63.4,\n" +
            "                    \"pressure\": 982.5\n" +
            "                },\n" +
            "                \"light\": 598.4,\n" +
            "                \"lamps\": {\n" +
            "                    \"yellow\": true,\n" +
            "                    \"orange\": true,\n" +
            "                    \"red\": false\n" +
            "                }\n" +
            "            }\n" +
            "        },\n" +
            "        \"secondary\":{\n" +
            "            \"properties\":{\n" +
            "                \"velocity\": 15.4,\n" +
            "                \"state\": \"moving\",\n" +
            "                \"direction\": 54.4\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "  }";
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
                    "    let name = \"jmh-test\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"/\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let value = JSON.parse(textPayload);\n" +
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

    public Test2ParseJsonPayloadToDitto() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE);
        externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(MAPPING_JSON)
                .build();
    }

    @Override
    public MessageMapper getMessageMapper() {
        final ActorSystem actorSystem = ActorSystem.create("Test", CONFIG);
        final MessageMapper javaScriptRhinoMapperPlain =
                JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlain.configure(CONNECTION, CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("ditto", Collections.emptyMap())
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

