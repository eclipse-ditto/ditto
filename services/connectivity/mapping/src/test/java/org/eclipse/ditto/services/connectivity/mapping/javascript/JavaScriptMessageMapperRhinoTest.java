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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests the {@link JavaScriptMessageMapperRhino} by initializing different mapping templates and ensuring that they
 * work as expected.
 * <p>
 * Uncomment the @RunWith(Parameterized.class) and  @Parameterized.Parameters method in order to execute the mapping
 * several times to find out how it performs once the JVM warmed up.
 */
//@RunWith(Parameterized.class)
public class JavaScriptMessageMapperRhinoTest {

//    @Parameterized.Parameters
//    public static List<Object[]> data() {
//        return Arrays.asList(new Object[20][0]);
//    }

    private static final String CONTENT_TYPE_PLAIN = "text/plain";
    private static final String CONTENT_TYPE_BINARY = "application/octet-stream";

    private static final String HEADER_CORRELATION_ID = "correlation-id";

    private static final String MAPPING_INCOMING_NAMESPACE = "org.eclipse.ditto";
    private static final String MAPPING_INCOMING_ID = "fancy-car-11";
    private static final String MAPPING_INCOMING_PATH = "/attributes/foo";

    private static final String MAPPING_INCOMING_PAYLOAD_STRING = "hello!";
    private static final ByteBuffer MAPPING_INCOMING_PAYLOAD_BYTES = ByteBuffer.wrap(
            MAPPING_INCOMING_PAYLOAD_STRING.getBytes(StandardCharsets.UTF_8));

    private final static Config MAPPING_CONFIG = ConfigFactory.parseString("javascript {\n" +
            "        maxScriptSizeBytes = 50000 # 50kB\n" +
            "        maxScriptExecutionTime = 500ms\n" +
            "        maxScriptStackDepth = 10\n" +
            "      }");


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
                    "    let namespace = \"" + MAPPING_INCOMING_NAMESPACE + "\";\n" +
                    "    let id = \"" + MAPPING_INCOMING_ID + "\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"" + MAPPING_INCOMING_PATH + "\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let value = textPayload;\n" +
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

    private static final String MAPPING_OUTGOING_PLAIN = "function mapFromDittoProtocolMsg(\n" +
            "    namespace,\n" +
            "    id,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value\n" +
            ") {\n" +
            "\n" +
            "    // ###\n" +
            "    // Insert your mapping logic here\n" +
            "    let headers = {};\n" +
            "    headers['correlation-id'] = dittoHeaders['correlation-id'];\n" +
            "    let textPayload = \"Thing ID was: \" + namespace + \":\" + id;\n" +
            "    let bytePayload = null;\n" +
            "    let contentType = \"" + CONTENT_TYPE_PLAIN + "\";\n" +
            "    // ###\n" +
            "\n" +
            "     return Ditto.buildExternalMsg(\n" +
            "        headers,\n" +
            "        textPayload,\n" +
            "        bytePayload,\n" +
            "        contentType\n" +
            "    );" +
            "}";

    private static final String MAPPING_INCOMING_EMPTY =
            "function mapToDittoProtocolMsg(\n" +
                    "    headers,\n" +
                    "    textPayload,\n" +
                    "    bytePayload,\n" +
                    "    contentType\n" +
                    ") {\n" +
                    "\n" +
                    "    return null;\n" +
                    "}";

    private static final String MAPPING_OUTGOING_EMPTY = "function mapFromDittoProtocolMsg(\n" +
            "    namespace,\n" +
            "    id,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value\n" +
            ") {\n" +
            "\n" +
            "    return null;" +
            "}";

    private static final String MAPPING_INCOMING_BINARY =
            "function mapToDittoProtocolMsg(\n" +
                    "    headers,\n" +
                    "    textPayload,\n" +
                    "    bytePayload,\n" +
                    "    contentType\n" +
                    ") {\n" +
                    "\n" +
                    "    // ###\n" +
                    "    // Insert your mapping logic here\n" +
                    "    let namespace = \"" + MAPPING_INCOMING_NAMESPACE + "\";\n" +
                    "    let id = \"" + MAPPING_INCOMING_ID + "\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"" + MAPPING_INCOMING_PATH + "\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let value = Ditto.arrayBufferToString(bytePayload);\n" +
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

    private static final String MAPPING_OUTGOING_BINARY = "function mapFromDittoProtocolMsg(\n" +
            "    namespace,\n" +
            "    id,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value\n" +
            ") {\n" +
            "\n" +
            "    // ###\n" +
            "    // Insert your mapping logic here\n" +
            "    let headers = {};\n" +
            "    headers['correlation-id'] = dittoHeaders['correlation-id'];\n" +
            "    let textPayload = null;\n" +
            "    let thingId = namespace + \":\" + id;\n" +
            "    let bytePayload = Ditto.stringToArrayBuffer(thingId);\n" +
            "    let contentType = \"" + CONTENT_TYPE_BINARY + "\";\n" +
            "    // ###\n" +
            "\n" +
            "     return Ditto.buildExternalMsg(\n" +
            "        headers,\n" +
            "        textPayload,\n" +
            "        bytePayload,\n" +
            "        contentType\n" +
            "    );" +
            "}";

    private static MessageMapper javaScriptRhinoMapperPlain;
    private static MessageMapper javaScriptRhinoMapperEmpty;
    private static MessageMapper javaScriptRhinoMapperBinary;

    @BeforeClass
    public static void setup() {
        javaScriptRhinoMapperPlain = MessageMappers.createJavaScriptMessageMapper();
        javaScriptRhinoMapperPlain.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                        .contentType(CONTENT_TYPE_PLAIN)
                        .incomingScript(MAPPING_INCOMING_PLAIN)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build()
        );

        javaScriptRhinoMapperEmpty = MessageMappers.createJavaScriptMessageMapper();
        javaScriptRhinoMapperEmpty.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                        .contentType(CONTENT_TYPE_PLAIN)
                        .incomingScript(MAPPING_INCOMING_EMPTY)
                        .outgoingScript(MAPPING_OUTGOING_EMPTY)
                        .build()
        );

        javaScriptRhinoMapperBinary = MessageMappers.createJavaScriptMessageMapper();
        javaScriptRhinoMapperBinary.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                        .contentType(CONTENT_TYPE_BINARY)
                        .incomingScript(MAPPING_INCOMING_BINARY)
                        .outgoingScript(MAPPING_OUTGOING_BINARY)
                        .build()
        );
    }

    @Test
    public void testPlainJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_PLAIN);
        final ExternalMessage message = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                .withText(MAPPING_INCOMING_PAYLOAD_STRING)
                .build();


        final long startTs = System.nanoTime();
        final Optional<Adaptable> adaptableOpt = javaScriptRhinoMapperPlain.map(message);
        final Adaptable adaptable = adaptableOpt.get();
        System.out.println(adaptable);
        System.out.println(
                "testPlainJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(adaptable.getTopicPath().getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(adaptable.getTopicPath().getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFY);
        assertThat(adaptable.getTopicPath().getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getId()).isEqualTo(MAPPING_INCOMING_ID);
        assertThat((Iterable<JsonKey>) adaptable.getPayload().getPath()).isEqualTo(
                JsonPointer.of(MAPPING_INCOMING_PATH));
        assertThat(adaptable.getPayload().getValue()).contains(JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING));
    }

    @Test
    public void testPlainJavascriptOutgoingMapping() {
        final String thingId = "org.eclipse.ditto:foo-bar-plain";
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();

        final Optional<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperPlain.map(adaptable);
        final ExternalMessage rawMessage = rawMessageOpt.get();
        System.out.println(rawMessage);
        System.out.println(
                "testPlainJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_PLAIN);
        assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
        assertThat(rawMessage.isTextMessage()).isTrue();
        assertThat(rawMessage.getTextPayload()).contains("Thing ID was: " + thingId);
    }

    @Test
    public void testEmptyJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_PLAIN);
        final ExternalMessage message = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                .build();


        final long startTs = System.nanoTime();
        final Optional<Adaptable> adaptableOpt = javaScriptRhinoMapperEmpty.map(message);
        System.out.println(
                "testEmptyJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(adaptableOpt).isEmpty();
    }

    @Test
    public void testEmptyJavascriptOutgoingMapping() {
        final String thingId = "org.eclipse.ditto:foo-bar-empty";
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();

        final Optional<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperEmpty.map(adaptable);
        System.out.println(
                "testEmptyJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(rawMessageOpt).isEmpty();
    }

    @Test
    public void testBinaryJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_BINARY);
        final ExternalMessage message = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                .withBytes(MAPPING_INCOMING_PAYLOAD_BYTES)
                .build();

        final long startTs = System.nanoTime();
        final Optional<Adaptable> adaptableOpt = javaScriptRhinoMapperBinary.map(message);
        final Adaptable adaptable = adaptableOpt.get();
        System.out.println(adaptable);
        System.out.println(
                "testBinaryJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(adaptable.getTopicPath().getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(adaptable.getTopicPath().getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFY);
        assertThat(adaptable.getTopicPath().getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getId()).isEqualTo(MAPPING_INCOMING_ID);
        assertThat((Iterable<JsonKey>) adaptable.getPayload().getPath()).isEqualTo(
                JsonPointer.of(MAPPING_INCOMING_PATH));
        assertThat(adaptable.getPayload().getValue()).contains(JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING));
    }

    @Test
    public void testBinaryJavascriptOutgoingMapping() {
        final String thingId = "org.eclipse.ditto:foo-bar-binary";
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();
        final Optional<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperBinary.map(adaptable);
        final ExternalMessage rawMessage = rawMessageOpt.get();
        System.out.println(rawMessage);
        System.out.println(
                "testBinaryJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_BINARY);
        assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
        assertThat(rawMessage.isTextMessage()).isFalse();
        assertThat(rawMessage.isBytesMessage()).isTrue();
        assertThat(rawMessage.getTextPayload()).isEmpty();
        assertThat(rawMessage.getBytePayload()).map(buf -> byteBuffer2String(buf, StandardCharsets.UTF_8))
                .contains(thingId);
    }

    @Nullable
    private static String byteBuffer2String(@Nullable final ByteBuffer buf, Charset charset) {
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
}
