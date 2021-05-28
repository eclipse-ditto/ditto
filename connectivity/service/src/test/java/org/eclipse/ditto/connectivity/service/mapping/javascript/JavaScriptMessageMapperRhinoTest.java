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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.service.config.mapping.DefaultMappingConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests the {@link JavaScriptMessageMapperRhino} by initializing different mapping templates and ensuring that they
 * work as expected.
 * <p>
 * Uncomment the @RunWith(Parameterized.class) and  @Parameterized.Parameters method in order to execute the mapping
 * several times to find out how it performs once the JVM warmed up.
 */
public final class JavaScriptMessageMapperRhinoTest {

    private static final String CONTENT_TYPE_PLAIN = "text/plain";
    private static final String CONTENT_TYPE_BINARY = "application/octet-stream";

    private static final String HEADER_CORRELATION_ID = "correlation-id";

    private static final String MAPPING_INCOMING_NAMESPACE = "org.eclipse.ditto";
    private static final String MAPPING_INCOMING_NAME = "fancy-car-11";
    private static final String MAPPING_INCOMING_PATH = "/attributes/foo";

    private static final String MAPPING_INCOMING_PAYLOAD_STRING = "hello!";
    private static final ByteBuffer MAPPING_INCOMING_PAYLOAD_BYTES = ByteBuffer.wrap(
            MAPPING_INCOMING_PAYLOAD_STRING.getBytes(StandardCharsets.UTF_8));

    private static final MappingConfig MAPPING_CONFIG =
            DefaultMappingConfig.of(ConfigFactory.parseString(
                    "mapping {\n" +
                            "  javascript {\n" +
                            "    maxScriptSizeBytes = 50000 # 50kB\n" +
                            "    maxScriptExecutionTime = 500ms\n" +
                            "    maxScriptStackDepth = 10\n" +
                            "  }\n" +
                            "}"));

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
                    "    let name = \"" + MAPPING_INCOMING_NAME + "\";\n" +
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

    private static final String MAPPING_OUTGOING_PLAIN = "function mapFromDittoProtocolMsg(\n" +
            "    namespace,\n" +
            "    name,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value,\n" +
            "    status,\n" +
            "    extra\n" +
            ") {\n" +
            "\n" +
            "    // ###\n" +
            "    // Insert your mapping logic here\n" +
            "    let headers = {};\n" +
            "    headers['correlation-id'] = dittoHeaders['correlation-id'];\n" +
            "    let textPayload = \"Thing ID was: \" + namespace + \":\" + name;\n" +
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
            "    name,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value,\n" +
            "    status,\n" +
            "    extra\n" +
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
                    "    let name = \"" + MAPPING_INCOMING_NAME + "\";\n" +
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

    private static final String MAPPING_OUTGOING_BINARY = "function mapFromDittoProtocolMsg(\n" +
            "    namespace,\n" +
            "    name,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value,\n" +
            "    status,\n" +
            "    extra\n" +
            ") {\n" +
            "\n" +
            "    // ###\n" +
            "    // Insert your mapping logic here\n" +
            "    let headers = {};\n" +
            "    headers['correlation-id'] = dittoHeaders['correlation-id'];\n" +
            "    let textPayload = null;\n" +
            "    let thingId = namespace + \":\" + name;\n" +
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

    private static final String MAPPING_OUTGOING_CHANNEL_AS_VALUE = "function mapFromDittoProtocolMsg(\n" +
            "    namespace,\n" +
            "    name,\n" +
            "    group,\n" +
            "    channel,\n" +
            "    criterion,\n" +
            "    action,\n" +
            "    path,\n" +
            "    dittoHeaders,\n" +
            "    value,\n" +
            "    status,\n" +
            "    extra\n" +
            ") {\n" +
            "\n" +
            "    let headers = {};\n" +
            "    headers['correlation-id'] = dittoHeaders['correlation-id'];\n" +
            "    let textPayload = channel;\n" +
            "    let bytePayload = null;\n" +
            "    let contentType = \"" + CONTENT_TYPE_PLAIN + "\";\n" +
            "\n" +
            "     return Ditto.buildExternalMsg(\n" +
            "        headers,\n" +
            "        textPayload,\n" +
            "        bytePayload,\n" +
            "        contentType\n" +
            "    );" +
            "}";

    private static final String MAPPING_INCOMING_WITH_STATUS =
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
                    "    let name = \"" + MAPPING_INCOMING_NAME + "\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"" + MAPPING_INCOMING_PATH + "\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let value = textPayload;\n" +
                    "    let status = 204;\n" +
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
                    "        value,\n" +
                    "        status\n" +
                    "    );\n" +
                    "}";

    private static final String MAPPING_INCOMING_WITH_STATUS_AND_EXTRA =
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
                    "    let name = \"" + MAPPING_INCOMING_NAME + "\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"" + MAPPING_INCOMING_PATH + "\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let value = textPayload;\n" +
                    "    let status = 204;\n" +
                    "    let extra = {};\n" +
                    "    extra.attributes = {};\n" +
                    "    extra.attributes.enriched = 'field';\n" +
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
                    "        value,\n" +
                    "        status,\n" +
                    "        extra\n" +
                    "    );\n" +
                    "}";

    private static final String MAPPING_INCOMING_DEFAULT = new BufferedReader(new InputStreamReader(JavaScriptMessageMapperRhinoTest.class.getResourceAsStream(JavaScriptMessageMapperRhino.INCOMING_SCRIPT)))
            .lines()
            .collect(Collectors.joining("\n"));

    private static final String MAPPING_OUTGOING_DEFAULT = new BufferedReader(new InputStreamReader(JavaScriptMessageMapperRhinoTest.class.getResourceAsStream(JavaScriptMessageMapperRhino.OUTGOING_SCRIPT)))
            .lines()
            .collect(Collectors.joining("\n"));


    private static MessageMapper javaScriptRhinoMapperNoop;
    private static MessageMapper javaScriptRhinoMapperPlain;
    private static MessageMapper javaScriptRhinoMapperPlainWithStatus;
    private static MessageMapper javaScriptRhinoMapperPlainWithStatusAndExtra;
    private static MessageMapper javaScriptRhinoMapperEmpty;
    private static MessageMapper javaScriptRhinoMapperBinary;
    private static MessageMapper javaScriptRhinoMapperChannelAsValue;
    private static MessageMapper javaScriptRhinoMapperDefault;

    @BeforeClass
    public static void setup() {
        javaScriptRhinoMapperNoop = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperNoop.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("noop", Collections.emptyMap())
                        .incomingScript("")
                        .outgoingScript("")
                        .build()
        );

        javaScriptRhinoMapperPlain = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlain.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("plain", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_PLAIN)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build()
        );

        javaScriptRhinoMapperPlainWithStatus = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlainWithStatus.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("plainStatus", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_WITH_STATUS)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build()
        );

        javaScriptRhinoMapperPlainWithStatusAndExtra =
                JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlainWithStatusAndExtra.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("plainStatus", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_WITH_STATUS_AND_EXTRA)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build()
        );

        javaScriptRhinoMapperEmpty = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperEmpty.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("empty", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_EMPTY)
                        .outgoingScript(MAPPING_OUTGOING_EMPTY)
                        .build()
        );

        javaScriptRhinoMapperBinary = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperBinary.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("binary", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_BINARY)
                        .outgoingScript(MAPPING_OUTGOING_BINARY)
                        .build()
        );

        javaScriptRhinoMapperChannelAsValue = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperChannelAsValue.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("channelAsValue", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_BINARY)
                        .outgoingScript(MAPPING_OUTGOING_CHANNEL_AS_VALUE)
                        .build()
        );

        javaScriptRhinoMapperDefault = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperDefault.configure(MAPPING_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("default", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_DEFAULT)
                        .outgoingScript(MAPPING_OUTGOING_DEFAULT)
                        .build()
        );
    }

    @Test
    public void testNoopJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        final ModifyAttribute modifyAttribute =
                ModifyAttribute.of(ThingId.of(MAPPING_INCOMING_NAMESPACE, MAPPING_INCOMING_NAME),
                        JsonPointer.of("foo"),
                        JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING),
                        DittoHeaders.newBuilder()
                                .correlationId(correlationId)
                                .schemaVersion(JsonSchemaVersion.V_2)
                                .build());
        final Adaptable inputAdaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyAttribute);
        final JsonifiableAdaptable jsonifiableInputAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(inputAdaptable);
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(jsonifiableInputAdaptable.toJsonString())
                .build();

        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperNoop.map(message);
        final Adaptable mappedAdaptable = adaptables.get(0);
        System.out.println(mappedAdaptable);
        System.out.println(
                "testNoopJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(mappedAdaptable).isEqualTo(jsonifiableInputAdaptable);
    }

    @Test
    public void testDefaultJavascriptIncomingMappingForDittoProtocol() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        final ModifyAttribute modifyAttribute =
                ModifyAttribute.of(ThingId.of(MAPPING_INCOMING_NAMESPACE, MAPPING_INCOMING_NAME),
                        JsonPointer.of("foo"),
                        JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING),
                        DittoHeaders.newBuilder()
                                .correlationId(correlationId)
                                .schemaVersion(JsonSchemaVersion.V_2)
                                .build());
        final Adaptable inputAdaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyAttribute);
        final JsonifiableAdaptable jsonifiableInputAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(inputAdaptable);
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(jsonifiableInputAdaptable.toJsonString())
                .build();

        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperNoop.map(message);
        final Adaptable mappedAdaptable = adaptables.get(0);
        System.out.println(mappedAdaptable);
        System.out.println(
                "testDefaultJavascriptIncomingMappingForDittoProtocol Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(mappedAdaptable).isEqualTo(jsonifiableInputAdaptable);
    }

    @Test
    public void testDefaultJavascriptIncomingMappingForByteDittoProtocol() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        final ModifyAttribute modifyAttribute =
                ModifyAttribute.of(ThingId.of(MAPPING_INCOMING_NAMESPACE, MAPPING_INCOMING_NAME),
                        JsonPointer.of("foo"),
                        JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING),
                        DittoHeaders.newBuilder()
                                .correlationId(correlationId)
                                .schemaVersion(JsonSchemaVersion.V_2)
                                .build());
        final Adaptable inputAdaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyAttribute);
        final JsonifiableAdaptable jsonifiableInputAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(inputAdaptable);
        final ByteBuffer bytes = ByteBuffer.wrap(jsonifiableInputAdaptable.toJsonString().getBytes(StandardCharsets.UTF_8));
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withBytes(bytes)
                .build();

        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperNoop.map(message);
        final Adaptable mappedAdaptable = adaptables.get(0);
        System.out.println(mappedAdaptable);
        System.out.println(
                "testDefaultJavascriptIncomingMappingForByteDittoProtocol Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(mappedAdaptable).isEqualTo(jsonifiableInputAdaptable);
    }

    @Test
    public void testNoopJavascriptOutgoingMapping() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-plain");
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null,
                        DittoHeaders.newBuilder().correlationId(correlationId).putHeader("subject",
                                "{{topic:action-subject}}").build());
        final Adaptable inputAdaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);
        final JsonifiableAdaptable jsonifiableInputAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(inputAdaptable);

        final long startTs = System.nanoTime();

        final List<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperNoop.map(inputAdaptable);
        final ExternalMessage rawMessage = rawMessageOpt.get(0);

        System.out.println(rawMessage.getHeaders());

        System.out.println(rawMessage);
        System.out.println(
                "testNoopJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
        assertThat(rawMessage.isTextMessage()).isTrue();
        final String textPayload = rawMessage.getTextPayload().get();
        DittoJsonAssertions.assertThat(JsonFactory.readFrom(textPayload).asObject())
                .isEqualToIgnoringFieldDefinitions(jsonifiableInputAdaptable.toJson());
    }

    @Test
    public void testDefaultJavascriptOutgoingMapping() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-plain");
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null,
                        DittoHeaders.newBuilder().correlationId(correlationId).putHeader("subject",
                                "{{topic:action-subject}}").build());


        System.out.println("HEADERS: " + ProtocolFactory.newHeadersWithJsonContentType(createThing.getDittoHeaders()));

        System.out.println("CREATE THING :" + createThing);

        final Adaptable inputAdaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);
        final JsonifiableAdaptable jsonifiableInputAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(inputAdaptable);

        final long startTs = System.nanoTime();

        System.out.println("ADAPTABLE: " + jsonifiableInputAdaptable);
        System.out.println("ADAPTABLE TO JSON: " + jsonifiableInputAdaptable.toJsonString());

        final List<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperDefault.map(inputAdaptable);
        final ExternalMessage rawMessage = rawMessageOpt.get(0);

        System.out.println(rawMessage.getHeaders());

        System.out.println(rawMessage);
        System.out.println(
                "testNoopJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
        assertThat(rawMessage.isTextMessage()).isTrue();
        final String textPayload = rawMessage.getTextPayload().get();
        DittoJsonAssertions.assertThat(JsonFactory.readFrom(textPayload).asObject())
                .isEqualToIgnoringFieldDefinitions(jsonifiableInputAdaptable.toJson());
    }

    @Test
    public void testDefaultJavascriptOutgoingMappingWithStatus() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-plain");
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThingResponse createThingResponse = CreateThingResponse.of(newThing, DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .putHeader("subject", "{{topic:action-subject}}")
                .build());


        System.out.println(
                "HEADERS: " + ProtocolFactory.newHeadersWithJsonContentType(createThingResponse.getDittoHeaders()));

        System.out.println("CREATE THING RESPONSE :" + createThingResponse);

        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThingResponse);
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        final long startTs = System.nanoTime();

        System.out.println("ADAPTABLE: " + jsonifiableAdaptable);
        System.out.println("ADAPTABLE TO JSON: " + jsonifiableAdaptable.toJsonString());

        final List<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperDefault.map(adaptable);
        final ExternalMessage rawMessage = rawMessageOpt.get(0);

        System.out.println(rawMessage.getHeaders());

        System.out.println(rawMessage);
        System.out.println("testDefaultJavascriptOutgoingMappingWithStatus Duration: " +
                (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
        assertThat(rawMessage.isTextMessage()).isTrue();
        final String textPayload = rawMessage.getTextPayload().get();
        final JsonObject jsonPayload = JsonFactory.readFrom(textPayload).asObject();
        DittoJsonAssertions.assertThat(jsonPayload).isEqualToIgnoringFieldDefinitions(jsonifiableAdaptable.toJson());
    }

    @Test
    public void testDefaultJavascriptOutgoingMappingForPolicyAnnouncements() {
        final PolicyId policyId = PolicyId.of("org.eclipse.ditto:foo-bar-policy");
        final String correlationId = UUID.randomUUID().toString();
        final SubjectDeletionAnnouncement announcement = SubjectDeletionAnnouncement.of(policyId, Instant.now(), List.of(),
                DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        assertThat(javaScriptRhinoMapperDefault.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testDefaultJavascriptOutgoingMappingForPolicyAnnouncements Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            final String textPayload = rawMessage.getTextPayload().get();
            final JsonObject jsonPayload = JsonFactory.readFrom(textPayload).asObject();
            DittoJsonAssertions.assertThat(jsonPayload).isEqualToIgnoringFieldDefinitions(jsonifiableAdaptable.toJson());
        });
    }

    @Test
    public void testDefaultJavascriptOutgoingMappingForConnectionAnnouncements() {
        final ConnectionId connectionId = ConnectionId.of("foo-bar-connection");
        final String correlationId = UUID.randomUUID().toString();
        final ConnectionOpenedAnnouncement announcement =
                ConnectionOpenedAnnouncement.of(connectionId, Instant.now(), DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        assertThat(javaScriptRhinoMapperDefault.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testDefaultJavascriptOutgoingMappingForConnectionAnnouncements Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            final String textPayload = rawMessage.getTextPayload().get();
            final JsonObject jsonPayload = JsonFactory.readFrom(textPayload).asObject();
            DittoJsonAssertions.assertThat(jsonPayload).isEqualToIgnoringFieldDefinitions(jsonifiableAdaptable.toJson());
        });
    }

    @Test
    public void testPlainJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_PLAIN);
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(MAPPING_INCOMING_PAYLOAD_STRING)
                .build();


        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperPlain.map(message);
        final Adaptable adaptable = adaptables.get(0);
        System.out.println(adaptable);
        System.out.println(
                "testPlainJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(adaptable.getTopicPath().getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(adaptable.getTopicPath().getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFY);
        assertThat(adaptable.getTopicPath().getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getEntityName()).isEqualTo(MAPPING_INCOMING_NAME);
        assertThat(adaptable.getPayload().getPath().toString()).isEqualTo(MAPPING_INCOMING_PATH);
        assertThat(adaptable.getPayload().getValue()).contains(JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING));
    }

    @Test
    public void testPlainJavascriptIncomingMappingWithStatus() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_PLAIN);
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-plain");
        final ModifyThingResponse modifyThingResponse = ModifyThingResponse.modified(thingId, DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .putHeader("subject", "{{topic:action-subject}}")
                .build());
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(modifyThingResponse.toJsonString())
                .build();


        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperPlainWithStatus.map(message);
        final Adaptable adaptable = adaptables.get(0);
        System.out.println(adaptable);
        System.out.println(
                "testPlainJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        assertThat(adaptable.getTopicPath().getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(adaptable.getTopicPath().getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFY);
        assertThat(adaptable.getTopicPath().getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getEntityName()).isEqualTo(MAPPING_INCOMING_NAME);
        assertThat(adaptable.getPayload().getPath().toString()).isEqualTo(MAPPING_INCOMING_PATH);
        assertThat(adaptable.getPayload().getValue()).map(JsonValue::asString)
                .contains(modifyThingResponse.toJsonString());
        assertThat(adaptable.getPayload().getHttpStatus()).contains(HttpStatus.NO_CONTENT);
    }

    @Test
    public void testPlainJavascriptIncomingMappingWithStatusAndExtra() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_PLAIN);
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-plain");
        final ModifyThingResponse modifyThingResponse = ModifyThingResponse.modified(thingId, DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .putHeader("subject", "{{topic:action-subject}}")
                .build());
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(modifyThingResponse.toJsonString())
                .build();

        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperPlainWithStatusAndExtra.map(message);
        final Adaptable adaptable = adaptables.get(0);
        System.out.println(adaptable);
        System.out.println(
                "testPlainJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(adaptable.getTopicPath()).satisfies(topicPath -> {
                softly.assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
                softly.assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
                softly.assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
                softly.assertThat(topicPath.getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
                softly.assertThat(topicPath.getEntityName()).isEqualTo(MAPPING_INCOMING_NAME);
            });
            softly.assertThat(adaptable.getPayload()).satisfies(payload -> {
                softly.assertThat(payload.getPath().toString()).isEqualTo(MAPPING_INCOMING_PATH);
                softly.assertThat(payload.getValue()).map(JsonValue::asString)
                        .contains(modifyThingResponse.toJsonString());
                softly.assertThat(payload.getHttpStatus()).contains(HttpStatus.NO_CONTENT);
                softly.assertThat(payload.getExtra()).contains(JsonObject.newBuilder()
                        .set("attributes", JsonObject.newBuilder().set("enriched", "field").build())
                        .build());
            });
        }
    }

    @Test
    public void testPlainJavascriptOutgoingMapping() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-plain");
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        assertThat(javaScriptRhinoMapperPlain.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testPlainJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_PLAIN);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            assertThat(rawMessage.getTextPayload()).contains("Thing ID was: " + thingId);
        });
    }

    @Test
    public void testEmptyJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_PLAIN);
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers).build();

        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperEmpty.map(message);
        System.out.println(
                "testEmptyJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 + "ms");

        assertThat(adaptables).isEmpty();
    }

    @Test
    public void testEmptyJavascriptOutgoingMapping() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-empty");
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();

        final List<ExternalMessage> rawMessageOpt = javaScriptRhinoMapperEmpty.map(adaptable);
        System.out.println(
                "testEmptyJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 + "ms");

        assertThat(rawMessageOpt).isEmpty();
    }

    @Test
    public void testBinaryJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_BINARY);
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withBytes(MAPPING_INCOMING_PAYLOAD_BYTES)
                .build();

        assertThat(javaScriptRhinoMapperBinary.map(message)).allSatisfy(adaptable -> {
            System.out.println(adaptable);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testBinaryJavascriptIncomingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(adaptable.getTopicPath()).satisfies(topicPath -> {
                assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
                assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
                assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
                assertThat(topicPath.getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
                assertThat(topicPath.getEntityName()).isEqualTo(MAPPING_INCOMING_NAME);
            });
            assertThat(adaptable.getPayload()).satisfies(payload -> {
                assertThat(payload.getPath().toString()).isEqualTo(MAPPING_INCOMING_PATH);
                assertThat(payload.getValue()).contains(JsonValue.of(MAPPING_INCOMING_PAYLOAD_STRING));
            });
        });
    }

    @Test
    public void testBinaryJavascriptOutgoingMapping() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:foo-bar-binary");
        final String correlationId = UUID.randomUUID().toString();
        final Thing newThing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        assertThat(javaScriptRhinoMapperBinary.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testBinaryJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_BINARY);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isFalse();
            assertThat(rawMessage.isBytesMessage()).isTrue();
            assertThat(rawMessage.getTextPayload()).isEmpty();
            assertThat(rawMessage.getBytePayload()).map(JavaScriptMessageMapperRhinoTest::byteBuffer2String)
                    .contains(thingId.toString());
        });
    }

    @Nullable
    private static String byteBuffer2String(@Nullable final ByteBuffer buf) {
        if (buf == null) {
            return null;
        }

        final byte[] bytes;
        if (buf.hasArray()) {
            bytes = buf.array();
        } else {
            buf.rewind();
            bytes = new byte[buf.remaining()];
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    public void testCorrectChannelForPolicyAnnouncementsJavascriptOutgoingMapping() {
        final PolicyId policyId = PolicyId.of("org.eclipse.ditto:foo-bar-policy");
        final String correlationId = UUID.randomUUID().toString();
        final SubjectDeletionAnnouncement announcement = SubjectDeletionAnnouncement.of(policyId, Instant.now(), List.of(),
                DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);

        assertThat(javaScriptRhinoMapperChannelAsValue.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testCorrectChannelForPolicyAnnouncementsJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_PLAIN);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            assertThat(rawMessage.isBytesMessage()).isFalse();
            assertThat(rawMessage.getTextPayload()).contains(TopicPath.Channel.NONE.getName());
        });
    }

    @Test
    public void testCorrectChannelForConnectionAnnouncementsJavascriptOutgoingMapping() {
        final ConnectionId connectionId = ConnectionId.of("foo-bar-connection");
        final String correlationId = UUID.randomUUID().toString();
        final ConnectionOpenedAnnouncement announcement =
                ConnectionOpenedAnnouncement.of(connectionId, Instant.now(), DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);

        assertThat(javaScriptRhinoMapperChannelAsValue.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            final long startTs = System.nanoTime();
            System.out.println(
                    "testCorrectChannelForConnectionAnnouncementsJavascriptOutgoingMapping Duration: " + (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_PLAIN);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            assertThat(rawMessage.isBytesMessage()).isFalse();
            assertThat(rawMessage.getTextPayload()).contains(TopicPath.Channel.NONE.getName());
        });
    }

}
