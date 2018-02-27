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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptPayloadMapperFactory;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * TODO doc
 *
 * Sorry, not really a test yet - class was used in order to manually test mapping functionality.
 */
//@RunWith(Parameterized.class)
@Ignore
public class PayloadMapperBytesTest {

    private static final String MAPPING_TEMPLATE = "ditto_mappingByteArray = [];" +
            "for (var i = 0; i < ditto_protocolJson.topic.length; i++){  \n" +
            "    ditto_mappingByteArray.push(ditto_protocolJson.topic.charCodeAt(i));\n" +
            "}";

    private static MessageMapper javaScriptRhinoMapper;

//    @Parameterized.Parameters
//    public static List<Object[]> data() {
//        return Arrays.asList(new Object[20][0]);
//    }

    @BeforeClass
    public static void setup() {
        javaScriptRhinoMapper = MessageMappers.createJavaScriptRhinoMapper();
        MessageMapperConfiguration configuration = JavaScriptPayloadMapperFactory.createJavaScriptOptionsBuilder
                (Collections.emptyMap()).outgoingMappingScript(MAPPING_TEMPLATE).build();
        javaScriptRhinoMapper.configure(configuration);
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
        final ExternalMessage rawMessage = javaScriptRhinoMapper.map(adaptable);
        System.out.println(rawMessage);
        System.out.println(byteBuffer2String(rawMessage.getBytePayload().orElse(null), StandardCharsets.UTF_8));
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }
}
