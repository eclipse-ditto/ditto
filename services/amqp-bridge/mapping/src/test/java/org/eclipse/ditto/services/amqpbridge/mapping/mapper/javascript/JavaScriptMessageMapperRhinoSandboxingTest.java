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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.MessageMappingFailedException;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
import org.junit.Test;

/**
 * Tests the {@link JavaScriptMessageMapperRhino} sandboxing capabilities by trying to exploit CPU time, exiting, etc.
 */
public class JavaScriptMessageMapperRhinoSandboxingTest {

    @Test
    public void ensureExitForbidden() {

        final MessageMapper mapper = createMapper("exit(1);");
        Assertions.assertThatExceptionOfType(MessageMappingFailedException.class)
                .isThrownBy(() -> mapper.map(createMessage()));
    }

    @Test
    public void ensureQuitForbidden() {

        final MessageMapper mapper = createMapper("quit(1);");
        Assertions.assertThatExceptionOfType(MessageMappingFailedException.class)
                .isThrownBy(() -> mapper.map(createMessage()));
    }

    @Test
    public void ensureEndlessLoopGetsAborted() {

        final MessageMapper mapper = createMapper("while (true);");
        final long startTs = System.nanoTime();
        Assertions.assertThatExceptionOfType(MessageMappingFailedException.class)
                .isThrownBy(() -> mapper.map(createMessage()));
        System.out.println(
                "ensureEndlessLoopGetsAborted aborted after: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }

    @Test
    public void ensureRecursionGetsAborted() {

        final MessageMapper mapper = createMapper("function recurse() {\n" +
                "  recurse();\n" +
                "};\n" +
                "recurse();");
        final long startTs = System.nanoTime();
        Assertions.assertThatExceptionOfType(MessageMappingFailedException.class)
                .isThrownBy(() -> mapper.map(createMessage()));
        System.out.println(
                "ensureRecursionGetsAborted aborted after: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }


    private MessageMapper createMapper(final String maliciousStuff) {
        final MessageMapper mapper = MessageMappers.createJavaScriptMessageMapper();
        mapper.configureWithValidation(
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                        .contentType("text/plain")
                        .incomingMappingScript(getMappingWrapperScript(maliciousStuff))
                        .build()
        );

        return mapper;
    }

    private ExternalMessage createMessage() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, "text/plain");
        return AmqpBridgeModelFactory.newExternalMessageBuilder(headers)
                .withText("what's up?")
                .build();
    }

    private static String getMappingWrapperScript(final String maliciousStuff) {
        return "function mapToDittoProtocolMsg(\n" +
                "    headers,\n" +
                "    textPayload,\n" +
                "    bytePayload,\n" +
                "    contentType\n" +
                ") {\n" +
                "\n" +
                "    // ###\n" +
                "    // Insert your mapping logic here\n" +
                "    " + maliciousStuff + "\n" +
                "    // ###\n" +
                "\n" +
                "    return buildDittoProtocolMsg(\n" +
                "        'org.eclipse.ditto',\n" +
                "        'should-not-come-trough',\n" +
                "        'things',\n" +
                "        'twin',\n" +
                "        'commands',\n" +
                "        'delete',\n" +
                "        '/',\n" +
                "        {},\n" +
                "        'oh oh!'\n" +
                "    );\n" +
                "}";
    }
}
