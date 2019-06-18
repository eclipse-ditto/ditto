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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests the {@link JavaScriptMessageMapperRhino} sandboxing capabilities by trying to exploit CPU time, exiting, etc.
 */
public final class JavaScriptMessageMapperRhinoSandboxingTest {

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

    @Test
    public void ensureTooBigMappingScriptIsNotLoaded() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            stringBuilder
                    .append("function foo").append(i)
                    .append("() { return ").append(i).append("; };")
                    .append('\n');
        }

        Assertions.assertThatExceptionOfType(MessageMapperConfigurationFailedException.class).isThrownBy(() ->
                createMapper(stringBuilder.toString())
        );
    }

    private static MessageMapper createMapper(final String maliciousStuff) {
        final MessageMapper mapper = MessageMappers.createJavaScriptMessageMapper();
        final MappingConfig mappingConfig =
                DefaultMappingConfig.of(ConfigFactory.parseString("javascript {\n" +
                        "        maxScriptSizeBytes = 50000 # 50kB\n" +
                        "        maxScriptExecutionTime = 500ms\n" +
                        "        maxScriptStackDepth = 10\n" +
                        "      }"));

        mapper.configure(mappingConfig,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                        .contentType("text/plain")
                        .incomingScript(getMappingWrapperScript(maliciousStuff))
                        .build()
        );

        return mapper;
    }

    private static ExternalMessage createMessage() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, "text/plain");
        return ExternalMessageFactory.newExternalMessageBuilder(headers)
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
                "    return Ditto.buildDittoProtocolMsg(\n" +
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
