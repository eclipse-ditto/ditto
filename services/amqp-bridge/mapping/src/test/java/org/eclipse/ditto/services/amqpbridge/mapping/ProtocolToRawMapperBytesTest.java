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
package org.eclipse.ditto.services.amqpbridge.mapping;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * TODO doc
 */
@RunWith(Parameterized.class)
public class ProtocolToRawMapperBytesTest {

    private static final ImmutableMappingTemplate TEMPLATE = new ImmutableMappingTemplate(
            "dittoProtocolJson.topic = 'org.eclipse.ditto/foo-bar/things/twin/commands/create';" +
            "dittoProtocolJson.path = '/';" +
            "dittoProtocolJson.headers = {};" +
            "dittoProtocolJson.headers['correlation-id'] = mappingHeaders['correlation-id'];" +
            "dittoProtocolJson.value = String.fromCharCode.apply(String, mappingByteArray);"
    );

    private static final ByteBuffer PAYLOAD_BYTEBUFFER = ByteBuffer.wrap("hello binary!".getBytes(StandardCharsets.UTF_8));

    private static PayloadMapper javaScriptNashornMapper;
    private static PayloadMapper javaScriptNashornSandboxMapper;
    private static PayloadMapper javaScriptRhinoMapper;

    @Parameterized.Parameters
    public static List<Object[]> data() {
        return Arrays.asList(new Object[20][0]);
    }

    @BeforeClass
    public static void setup() {
        javaScriptNashornMapper = PayloadMappers.createJavaScriptNashornMapper(
                PayloadMappers
                        .createJavaScriptPayloadMapperOptionsBuilder()
                        .loadMustacheJS(true)
                        .build());

        javaScriptNashornSandboxMapper = PayloadMappers.createJavaScriptNashornSandboxMapper(
                PayloadMappers
                        .createJavaScriptPayloadMapperOptionsBuilder()
                        .loadMustacheJS(true)
                        .build());

        javaScriptRhinoMapper = PayloadMappers.createJavaScriptRhinoMapper(
                PayloadMappers
                        .createJavaScriptPayloadMapperOptionsBuilder()
                        .loadMustacheJS(true)
                        .build());
    }

    @Test
    public void testNashornMapper() {
        System.out.println("\nNashorn");

        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "4711-foobar");
        final PayloadMapperMessage message = new ImmutablePayloadMapperMessage(PAYLOAD_BYTEBUFFER, null, headers);

        final long startTs = System.nanoTime();
        final Adaptable
                adaptable = javaScriptNashornMapper.mapIncomingMessageToDittoAdaptable(TEMPLATE, message);
        System.out.println(adaptable);
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }

    private static String byteBuffer2String(final ByteBuffer buf, Charset charset) {
        if (buf == null) {
            return null;
        }

        byte[] bytes;
        if (buf.hasArray()) {
            bytes = buf.array();
        } else {
            buf.rewind();
            bytes = new byte[buf.remaining()];
        }
        return new String(bytes, charset);
    }

    @Test
    public void testNashornSandboxMapper() {
        System.out.println("\nNashorn sandboxed");

        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "4711-foobar");
        final PayloadMapperMessage message = new ImmutablePayloadMapperMessage(PAYLOAD_BYTEBUFFER, null, headers);

        final long startTs = System.nanoTime();
        final Adaptable
                adaptable = javaScriptNashornSandboxMapper.mapIncomingMessageToDittoAdaptable(TEMPLATE, message);
        System.out.println(adaptable);
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }

    @Test
    public void testRhinoMapper() {
        System.out.println("\nRhino");

        final Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "4711-foobar");
        final PayloadMapperMessage message = new ImmutablePayloadMapperMessage(PAYLOAD_BYTEBUFFER, null, headers);

        final long startTs = System.nanoTime();
        final Adaptable
                adaptable = javaScriptRhinoMapper.mapIncomingMessageToDittoAdaptable(TEMPLATE, message);
        System.out.println(adaptable);
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }
}
