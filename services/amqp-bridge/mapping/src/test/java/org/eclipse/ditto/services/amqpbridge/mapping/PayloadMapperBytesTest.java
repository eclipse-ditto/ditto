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
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * TODO doc
 */
@RunWith(Parameterized.class)
public class PayloadMapperBytesTest {

    private static final ImmutableMappingTemplate TEMPLATE = new ImmutableMappingTemplate("mappingByteArray = [];" +
            "for (var i = 0; i < dittoProtocolJson.topic.length; i++){  \n" +
            "    mappingByteArray.push(dittoProtocolJson.topic.charCodeAt(i));\n" +
            "}");

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
        final Thing newThing = Thing.newBuilder()
                .setId("org.eclipse.ditto:foo-bar")
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId("cor-0815").build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();

        final PayloadMapperMessage
                rawMessage = javaScriptNashornMapper.mapOutgoingMessageFromDittoAdaptable(TEMPLATE, adaptable);
        System.out.println(rawMessage);
        System.out.println(byteBuffer2String(rawMessage.getRawData().orElse(null), StandardCharsets.UTF_8));
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
        final Thing newThing = Thing.newBuilder()
                .setId("org.eclipse.ditto:foo-bar")
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId("cor-0815").build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();
        final PayloadMapperMessage
                rawMessage = javaScriptNashornSandboxMapper.mapOutgoingMessageFromDittoAdaptable(TEMPLATE, adaptable);
        System.out.println(rawMessage);
        System.out.println(byteBuffer2String(rawMessage.getRawData().orElse(null), StandardCharsets.UTF_8));
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }

    @Test
    public void testRhinoMapper() {
        System.out.println("\nRhino");
        final Thing newThing = Thing.newBuilder()
                .setId("org.eclipse.ditto:foo-bar")
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId("cor-0815").build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();
        final PayloadMapperMessage
                rawMessage = javaScriptRhinoMapper.mapOutgoingMessageFromDittoAdaptable(TEMPLATE, adaptable);
        System.out.println(rawMessage);
        System.out.println(byteBuffer2String(rawMessage.getRawData().orElse(null), StandardCharsets.UTF_8));
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }
}
