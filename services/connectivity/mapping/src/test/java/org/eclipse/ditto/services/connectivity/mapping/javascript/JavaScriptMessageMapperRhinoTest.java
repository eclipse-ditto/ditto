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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
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
//@RunWith(Parameterized.class)
public final class JavaScriptMessageMapperRhinoTest {

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
            "    value,\n" +
            "    status,\n" +
            "    extra\n" +
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
                    "    let id = \"" + MAPPING_INCOMING_ID + "\";\n" +
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
                    "        id,\n" +
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
                    "    let id = \"" + MAPPING_INCOMING_ID + "\";\n" +
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
                    "        id,\n" +
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

    private static final String MAPPING_INCOMING_DEFAULT = "function mapToDittoProtocolMsg(\n" +
            "  headers,\n" +
            "  textPayload,\n" +
            "  bytePayload,\n" +
            "  contentType\n" +
            ") {\n" +
            "\n" +
            "  // ###\n" +
            "  // Insert your mapping logic here:\n" +
            "  if (contentType === 'application/vnd.eclipse.ditto+json') {\n" +
            "    let dittoProtocolMsg = JSON.parse(textPayload);\n" +
            "    Object.assign(dittoProtocolMsg.headers, headers);\n" +
            "    return dittoProtocolMsg;\n" +
            "  }\n" +
            "  if (headers) {\n" +
            "    return null; // returning 'null' means that the message will be dropped\n" +
            "    // TODO replace with something useful\n" +
            "  }\n" +
            "  // ###\n" +
            "\n" +
            "  return Ditto.buildDittoProtocolMsg(\n" +
            "    namespace, // The namespace of the entity in java package notation, e.g.: \"org.eclipse.ditto\"\n" +
            "    id, // The ID of the entity\n" +
            "    group, // The affected group/entity, one of: \"things\"\n" +
            "    channel, // The channel for the signal, one of: \"twin\"|\"live\"\n" +
            "    criterion, // The criterion to apply, one of: \"commands\"|\"events\"|\"search\"|\"messages\"|\"errors\"\n" +
            "    action, // The action to perform, one of: \"create\"|\"retrieve\"|\"modify\"|\"delete\"\n" +
            "    path, // The path which is affected by the message, e.g.: \"/attributes\"\n" +
            "    dittoHeaders, // The headers Object containing all Ditto Protocol header values\n" +
            "    value, // The value to apply / which was applied (e.g. in a \"modify\" action)\n" +
            "    status, // The status code that indicates the result of the command\n" +
            "    extra // The enriched extra fields\n" +
            "  );\n" +
            "}\n";

    private static final String MAPPING_OUTGOING_DEFAULT = "function mapFromDittoProtocolMsg(\n" +
            "  namespace,\n" +
            "  id,\n" +
            "  group,\n" +
            "  channel,\n" +
            "  criterion,\n" +
            "  action,\n" +
            "  path,\n" +
            "  dittoHeaders,\n" +
            "  value,\n" +
            "  status,\n" +
            "  extra\n" +
            ") {\n" +
            "\n" +
            "  // ###\n" +
            "  // Insert your mapping logic here:\n" +
            "  let headers = dittoHeaders;\n" +
            "  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, id, group, channel, criterion, action, path, dittoHeaders, value, status));\n" +
            "  // TODO replace with something useful, this will publish the message in Ditto Protocol JSON\n" +
            "  let bytePayload = null;\n" +
            "  let contentType = 'application/vnd.eclipse.ditto+json';\n" +
            "  // ###\n" +
            "\n" +
            "  return Ditto.buildExternalMsg(\n" +
            "    headers, // The external headers Object containing header values\n" +
            "    textPayload, // The external mapped String\n" +
            "    bytePayload, // The external mapped byte[]\n" +
            "    contentType // The returned Content-Type\n" +
            "  );\n" +
            "}\n";

    private static MessageMapper javaScriptRhinoMapperNoop;
    private static MessageMapper javaScriptRhinoMapperPlain;
    private static MessageMapper javaScriptRhinoMapperPlainWithStatus;
    private static MessageMapper javaScriptRhinoMapperPlainWithStatusAndExtra;
    private static MessageMapper javaScriptRhinoMapperEmpty;
    private static MessageMapper javaScriptRhinoMapperBinary;
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

        javaScriptRhinoMapperPlainWithStatusAndExtra = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
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
                ModifyAttribute.of(ThingId.of(MAPPING_INCOMING_NAMESPACE, MAPPING_INCOMING_ID),
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


        System.out.println("HEADERS: " + ProtocolFactory.newHeadersWithDittoContentType(createThing.getDittoHeaders()));

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
                "HEADERS: " + ProtocolFactory.newHeadersWithDittoContentType(createThingResponse.getDittoHeaders()));

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
        assertThat(adaptable.getTopicPath().getId()).isEqualTo(MAPPING_INCOMING_ID);
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
        assertThat(adaptable.getTopicPath().getId()).isEqualTo(MAPPING_INCOMING_ID);
        assertThat(adaptable.getPayload().getPath().toString()).isEqualTo(MAPPING_INCOMING_PATH);
        assertThat(adaptable.getPayload().getValue()).map(JsonValue::asString).contains(modifyThingResponse.toJsonString());
        assertThat(adaptable.getPayload().getStatus()).contains(HttpStatusCode.NO_CONTENT);
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
                softly.assertThat(topicPath.getId()).isEqualTo(MAPPING_INCOMING_ID);
            });
            softly.assertThat(adaptable.getPayload()).satisfies(payload -> {
                softly.assertThat(payload.getPath().toString()).isEqualTo(MAPPING_INCOMING_PATH);
                softly.assertThat(payload.getValue()).map(JsonValue::asString)
                        .contains(modifyThingResponse.toJsonString());
                softly.assertThat(payload.getStatus()).contains(HttpStatusCode.NO_CONTENT);
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
                assertThat(topicPath.getId()).isEqualTo(MAPPING_INCOMING_ID);
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

}
