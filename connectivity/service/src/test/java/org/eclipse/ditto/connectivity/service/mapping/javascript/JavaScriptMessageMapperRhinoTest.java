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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

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

    private static final Connection CONNECTION = TestConstants.createConnection();
    private static final Config CONFIG = ConfigFactory.parseString(
                    "mapping {\n" +
                            "  javascript {\n" +
                            "    maxScriptSizeBytes = 50000 # 50kB\n" +
                            "    maxScriptExecutionTime = 5000ms\n" +
                            "    maxScriptStackDepth = 25\n" +
                            "    commonJsModulePath = \"./target/test-classes/unpacked-test-webjars\"\n" +
                            "  }\n" +
                            "}")
            .atKey("ditto.connectivity")
            .withFallback(ConfigFactory.load("test"));
    private static final ConnectivityConfig CONNECTIVITY_CONFIG = ConnectivityConfig.of(CONFIG);

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
            "    let textPayload = `Thing ID was: ${namespace}:${name}`;\n" +
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
            "    let thingId = `${namespace}:${name}`;\n" +
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

    private static final String MAPPING_INCOMING_BINARY_BYTEBUFFER_JS =
            "function mapToDittoProtocolMsg(\n" +
                    "    headers,\n" +
                    "    textPayload,\n" +
                    "    bytePayload,\n" +
                    "    contentType\n" +
                    ") {\n" +
                    "\n" +
                    "    // ###\n" +
                    "    // Insert your mapping logic here\n" +
                    "    function intFromBytes(arrayBuffer){\n" +
                    "       let byteBuf = Ditto.asByteBuffer(arrayBuffer);\n" +
                    "       return parseInt(byteBuf.toHex(), 16);\n" +
                    "    };\n" +
                    "    let namespace = \"" + MAPPING_INCOMING_NAMESPACE + "\";\n" +
                    "    let name = \"" + MAPPING_INCOMING_NAME + "\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"" + MAPPING_INCOMING_PATH + "\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let theBytes = intFromBytes(bytePayload);\n" +
                    "    let value = {\n" +
                    "       a: theBytes & 0b1111,\n" +
                    "       b: (theBytes >>> 4) & 0b1111,\n" +
                    "       c: 99\n" +
                    "    };\n" +
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

    private static final String COMMON_PROTOBUF_MODEL = "    var proto = \"\\\n" +
            "    syntax = \\\"proto3\\\";\\\n" +
            "    \\\n" +
            "    message Header { \\\n" +
            "        string message_type = 1; \\\n" +
            "        int64 timestamp_ms = 2;\\\n" +
            "        string message_id = 3;\\\n" +
            "        string device_id = 4;\\\n" +
            "        string boot_id = 5;\\\n" +
            "    }\\\n" +
            "    message Image {\\\n" +
            "        int64 timestamp_ms = 1;\\\n" +
            "        string id = 2;\\\n" +
            "        string camera_identifier = 3;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message GenericMessage {\\\n" +
            "        Header header = 1;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    enum ErrorCode {\\\n" +
            "        ERROR__NO_ERROR = 0;\\\n" +
            "        ERROR__FEATURE_NOT_AVAILABLE = 1;\\\n" +
            "        ERROR__FEATURE_TEMPORARILY_NOT_AVAILABLE = 2;\\\n" +
            "        ERROR__FEATURE_ALREADY_REQUESTED = 3;\\\n" +
            "        ERROR__FEATURE_NOT_AVAILABLE_DUE_IGNITION_STATE = 4;\\\n" +
            "        ERROR__OBJECTSTORE_UPLOAD_FAILED = 5;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message Status  {\\\n" +
            "        ErrorCode error_code = 1;\\\n" +
            "        string error_message = 2;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message SmokeEvent {\\\n" +
            "        Header header = 1;\\\n" +
            "        int64 smoke_start_timestamp_ms = 2;\\\n" +
            "        repeated Image images = 3;\\\n" +
            "        int32 duration_in_seconds = 4;\\\n" +
            "        double confidence = 5;\\\n" +
            "        double total_smoke = 6;\\\n" +
            "        double background_air_quality = 7;\\\n" +
            "        Status status = 8;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message SoftwareUpdateAction {\\\n" +
            "        string correlationId = 1;\\\n" +
            "        repeated SoftwareModuleAction softwareModules = 2;\\\n" +
            "        int32 weight = 3;\\\n" +
            "        map<string, string> metaData = 4;\\\n" +
            "        bool forced = 5;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message SoftwareModuleAction {\\\n" +
            "        SoftwareModuleId softwareModule = 1;\\\n" +
            "        repeated SoftwareArtifactAction artifacts = 2;\\\n" +
            "        map<string, string> metadata = 3;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message SoftwareModuleId {\\\n" +
            "        string name = 1;\\\n" +
            "        string version = 2;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message SoftwareArtifactAction {\\\n" +
            "        string filename = 1;\\\n" +
            "        map<string, Links> download = 2;\\\n" +
            "        map<string, string> checksums = 3;\\\n" +
            "    }\\\n" +
            "    \\\n" +
            "    message Links {\\\n" +
            "        string url = 1;\\\n" +
            "        string md5url = 2;\\\n" +
            "    }\\\n" +
            "    \";\n" +
            "    \n" +
            "    function toCamelCase(str) {\n" +
            "        return str.substring(0,1) + str.substring(1).replace(/_([a-z])(?=[a-z]|$)/g, function($0, $1) { return $1.toUpperCase(); });\n" +
            "    }\n" +
            "    \n" +
            "    function addAliasProperty(type, name, aliasName) {\n" +
            "        if (aliasName !== name)\n" +
            "            Object.defineProperty(type.ctor.prototype, aliasName, {\n" +
            "                get: function() { return this[name]; },\n" +
            "                set: function(value) { this[name] = value; }\n" +
            "            });\n" +
            "    }\n" +
            "    \n" +
            "    function addVirtualCamelcaseFields(type) {\n" +
            "        type.fieldsArray.forEach(function(field) {\n" +
            "            addAliasProperty(type, field.name, toCamelCase(field.name));\n" +
            "        });\n" +
            "        type.oneofsArray.forEach(function(oneof) {\n" +
            "            addAliasProperty(type, oneof.name, toCamelCase(oneof.name));\n" +
            "        });\n" +
            "        return type;\n" +
            "    }\n" +
            "    \n" +
            "    var protobuf = require(\"protobuf\");\n" +
            "    var root = protobuf.parse(proto, { keepCase: true }).root;\n";

    private static final String MAPPING_INCOMING_PROTOBUF_JS =
            "function mapToDittoProtocolMsg(\n" +
                    "    headers,\n" +
                    "    textPayload,\n" +
                    "    bytePayload,\n" +
                    "    contentType\n" +
                    ") {\n" +
                    "\n" +
                    "    // ###\n" +
                    "    // Insert your mapping logic here\n" +
                    COMMON_PROTOBUF_MODEL +
                    "    var SmokeEvent = addVirtualCamelcaseFields(root.lookup(\"SmokeEvent\"));\n" +
                    "    \n" +
                    "    var decodedSmokeEvent = SmokeEvent.decode(new Uint8Array(bytePayload));\n" +
                    "    \n" +
                    "    let namespace = \"" + MAPPING_INCOMING_NAMESPACE + "\";\n" +
                    "    let name = \"" + MAPPING_INCOMING_NAME + "\";\n" +
                    "    let group = \"things\";\n" +
                    "    let channel = \"twin\";\n" +
                    "    let criterion = \"commands\";\n" +
                    "    let action = \"modify\";\n" +
                    "    let path = \"" + MAPPING_INCOMING_PATH + "\";\n" +
                    "    let dittoHeaders = {};\n" +
                    "    dittoHeaders[\"correlation-id\"] = headers[\"correlation-id\"];\n" +
                    "    let value = decodedSmokeEvent;\n" +
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


    private static final JsonValue MAPPING_INCOMING_PROTOBUF_PARSED = JsonFactory.readFrom(
            "{\"header\":{\"message_type\":\"SmokeEvent\",\"timestamp_ms\":\"1595043218316\",\"message_id\":\"95516572-e737-4c90-a5cd-7448f0bcaf37\",\"device_id\":\"com.bosch.cm.ivs_IVS-INTEGRATION-TEST-DEVICE\",\"boot_id\":\"a4e376c3-a288-4db8-8a7b-4657dea5f515\"},\"smoke_start_timestamp_ms\":\"1591096889617\",\"images\":[{\"timestamp_ms\":\"1591251430839\",\"id\":\"39d342bc-93b7-4765-ace6-5ae833739431\",\"camera_identifier\":\"CAM-REAR-01\"}],\"duration_in_seconds\":20,\"confidence\":220,\"total_smoke\":125,\"background_air_quality\":10,\"status\":{}}");

    private static final ByteBuffer MAPPING_INCOMING_PROTOBUF_BYTES = ByteBuffer.wrap(
            Base64.getDecoder()
                    .decode("Co0BCgpTbW9rZUV2ZW50EIyH8P+1LhokOTU1MTY1NzItZTczNy00YzkwLWE1Y2QtNzQ0OGYwYmNhZjM3Iixjb20uYm9zY2guY20uaXZzX0lWUy1JTlRFR1JBVElPTi1URVNULURFVklDRSokYTRlMzc2YzMtYTI4OC00ZGI4LThhN2ItNDY1N2RlYTVmNTE1EJGij6anLho6CLfb5++nLhIkMzlkMzQyYmMtOTNiNy00NzY1LWFjZTYtNWFlODMzNzM5NDMxGgtDQU0tUkVBUi0wMSAUKQAAAAAAgGtAMQAAAAAAQF9AOQAAAAAAACRAQgA=")
    );

    private static final String MAPPING_OUTGOING_PROTOBUF_JS =
            "function mapFromDittoProtocolMsg(\n" +
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
                    "    // ###\n" +
                    "    // Insert your mapping logic here\n" +
                    COMMON_PROTOBUF_MODEL +
                    "    var SoftwareUpdateAction = addVirtualCamelcaseFields(root.lookup(\"SoftwareUpdateAction\"));\n" +
                    "    var swUpdateMessage = SoftwareUpdateAction.create(value);\n" +
                    "    var buf = SoftwareUpdateAction.encode(swUpdateMessage).finish();\n" +
                    "    \n" +
                    "    return Ditto.buildExternalMsg(\n" +
                    "      dittoHeaders,\n" +
                    "      null,\n" +
                    "      buf,\n" +
                    "      'application/vnd.google.protobuf'\n" +
                    "    );\n" +
                    "}";

    private final static String MAPPING_OUTGOING_PROTOBUF_VALUE = "{\n" +
            "    \"metaData\":{\n" +
            "    },\n" +
            "    \"softwareModules\":[\n" +
            "        {\n" +
            "            \"metaData\":{\n" +
            "            },\n" +
            "            \"softwareModule\":{\n" +
            "                \"name\":\"SlimScaleyFirmware\",\n" +
            "                \"version\":\"1.0\"\n" +
            "            },\n" +
            "            \"artifacts\":[\n" +
            "                {\n" +
            "                    \"checksums\":{\n" +
            "                        \"SHA256\":\"my-sha256-checksum\",\n" +
            "                        \"SHA1\":\"my-sha1-checksum\",\n" +
            "                        \"MD5\":\"my-md5-checksum\"\n" +
            "                    },\n" +
            "                    \"download\":{\n" +
            "                        \"HTTPS\":{\n" +
            "                            \"md5url\":\"https://some-host.com/foo/8e965e8651da8cbfe5f70e4254e21698145c7bc0?Expires=1607695047&Signature=EUzC1ENuxTQcTV0JK8nw0pOobVQPqsr5~gshwkbGOplosXHTYF0oj4WnRaB5kmW0ikoRoezpTebGpDV0qHhNew-APhn0onvrW0hdUITsstp9aDqr02dXyzX-JCPYNg7WWgzdvOcaDTbB2k6z4~Vy9bjH3LX9vGoqG-7QnfhaPfinSGGbixAdEXZsctC8SdriqdHuaGhQQcI96UuGIlephA2Wdu7XkpVmG8vtoI~fRFVeO4NEdboUwSmn9gQPy7dGhRzfh9FEYnUf3Mk60-4j3WrRK979nIF9nIWh-HWW4hVdsQC1kIXLiUWukx5~DNShFiDVArzvhQMbgRflOFEXPQ__&Key-Pair-Id=APKAJ7V55VK3Y2WFZOHQ\",\n" +
            "                            \"url\":\"https://some-host.com/foo/8e965e8651da8cbfe5f70e4254e21698145c7bc0?Expires=1607695047&Signature=EUzC1ENuxTQcTV0JK8nw0pOobVQPqsr5~gshwkbGOplosXHTYF0oj4WnRaB5kmW0ikoRoezpTebGpDV0qHhNew-APhn0onvrW0hdUITsstp9aDqr02dXyzX-JCPYNg7WWgzdvOcaDTbB2k6z4~Vy9bjH3LX9vGoqG-7QnfhaPfinSGGbixAdEXZsctC8SdriqdHuaGhQQcI96UuGIlephA2Wdu7XkpVmG8vtoI~fRFVeO4NEdboUwSmn9gQPy7dGhRzfh9FEYnUf3Mk60-4j3WrRK979nIF9nIWh-HWW4hVdsQC1kIXLiUWukx5~DNShFiDVArzvhQMbgRflOFEXPQ__&Key-Pair-Id=APKAJ7V55VK3Y2WFZOHQ\"\n" +
            "                        }\n" +
            "                    },\n" +
            "                    \"filename\":\"The_Mandalorian_season_2_poster_1603708332443.webp\",\n" +
            "                    \"size\":243298\n" +
            "                }\n" +
            "            ]\n" +
            "        }\n" +
            "    ],\n" +
            "    \"forced\":true,\n" +
            "    \"weight\":null,\n" +
            "    \"correlationId\":\"cafbe5dc-13ea-401a-b25f-923810be5cd8\"\n" +
            "}";

    private static final String MAPPING_INCOMING_DEFAULT = new BufferedReader(new InputStreamReader(
            JavaScriptMessageMapperRhinoTest.class.getResourceAsStream(JavaScriptMessageMapperRhino.INCOMING_SCRIPT)))
            .lines()
            .collect(Collectors.joining("\n"));

    private static final String MAPPING_OUTGOING_DEFAULT = new BufferedReader(new InputStreamReader(
            JavaScriptMessageMapperRhinoTest.class.getResourceAsStream(JavaScriptMessageMapperRhino.OUTGOING_SCRIPT)))
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
    private static MessageMapper javaScriptRhinoMapperBinaryWithByteBufferJs;
    private static MessageMapper javaScriptRhinoMapperWithProtobufJs;

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setup() {
        actorSystem = ActorSystem.create("Test", CONFIG);
        javaScriptRhinoMapperNoop = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperNoop.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("noop", Collections.emptyMap())
                        .incomingScript("")
                        .outgoingScript("")
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperPlain = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlain.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("plain", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_PLAIN)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperPlainWithStatus = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlainWithStatus.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("plainStatus", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_WITH_STATUS)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperPlainWithStatusAndExtra =
                JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperPlainWithStatusAndExtra.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("plainStatus", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_WITH_STATUS_AND_EXTRA)
                        .outgoingScript(MAPPING_OUTGOING_PLAIN)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperEmpty = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperEmpty.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("empty", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_EMPTY)
                        .outgoingScript(MAPPING_OUTGOING_EMPTY)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperBinary = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperBinary.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("binary", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_BINARY)
                        .outgoingScript(MAPPING_OUTGOING_BINARY)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperChannelAsValue = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperChannelAsValue.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("channelAsValue", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_BINARY)
                        .outgoingScript(MAPPING_OUTGOING_CHANNEL_AS_VALUE)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperDefault = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperDefault.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("default", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_DEFAULT)
                        .outgoingScript(MAPPING_OUTGOING_DEFAULT)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperBinaryWithByteBufferJs =
                JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperBinaryWithByteBufferJs.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("binaryWithByteBufferJS",
                                Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_BINARY_BYTEBUFFER_JS)
                        .loadBytebufferJS(true)
                        .build(),
                actorSystem
        );

        javaScriptRhinoMapperWithProtobufJs = JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
        javaScriptRhinoMapperWithProtobufJs.configure(CONNECTION,
                CONNECTIVITY_CONFIG,
                JavaScriptMessageMapperFactory
                        .createJavaScriptMessageMapperConfigurationBuilder("withProtobufJS", Collections.emptyMap())
                        .incomingScript(MAPPING_INCOMING_PROTOBUF_JS)
                        .outgoingScript(MAPPING_OUTGOING_PROTOBUF_JS)
                        .build(),
                actorSystem
        );
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
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
                "testDefaultJavascriptIncomingMappingForDittoProtocol Duration: " +
                        (System.nanoTime() - startTs) / 1000000.0 + "ms");

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
        final ByteBuffer bytes =
                ByteBuffer.wrap(jsonifiableInputAdaptable.toJsonString().getBytes(StandardCharsets.UTF_8));
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withBytes(bytes)
                .build();

        final long startTs = System.nanoTime();
        final List<Adaptable> adaptables = javaScriptRhinoMapperNoop.map(message);
        final Adaptable mappedAdaptable = adaptables.get(0);
        System.out.println(mappedAdaptable);
        System.out.println(
                "testDefaultJavascriptIncomingMappingForByteDittoProtocol Duration: " +
                        (System.nanoTime() - startTs) / 1000000.0 + "ms");

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
        final SubjectDeletionAnnouncement announcement =
                SubjectDeletionAnnouncement.of(policyId, Instant.now(), List.of(),
                        DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperDefault.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            System.out.println(
                    "testDefaultJavascriptOutgoingMappingForPolicyAnnouncements Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            final String textPayload = rawMessage.getTextPayload().get();
            final JsonObject jsonPayload = JsonFactory.readFrom(textPayload).asObject();
            DittoJsonAssertions.assertThat(jsonPayload)
                    .isEqualToIgnoringFieldDefinitions(jsonifiableAdaptable.toJson());
        });
    }

    @Test
    public void testDefaultJavascriptOutgoingMappingForConnectionAnnouncements() {
        final ConnectionId connectionId = ConnectionId.of("foo-bar-connection");
        final String correlationId = UUID.randomUUID().toString();
        final ConnectionOpenedAnnouncement announcement =
                ConnectionOpenedAnnouncement.of(connectionId, Instant.now(),
                        DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperDefault.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            System.out.println(
                    "testDefaultJavascriptOutgoingMappingForConnectionAnnouncements Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            final String textPayload = rawMessage.getTextPayload().get();
            final JsonObject jsonPayload = JsonFactory.readFrom(textPayload).asObject();
            DittoJsonAssertions.assertThat(jsonPayload)
                    .isEqualToIgnoringFieldDefinitions(jsonifiableAdaptable.toJson());
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
        assertThat(adaptable.getTopicPath().getNamespace()).hasToString(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getEntityName()).hasToString(MAPPING_INCOMING_NAME);
        assertThat(adaptable.getPayload().getPath().toString()).hasToString(MAPPING_INCOMING_PATH);
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
        assertThat(adaptable.getTopicPath().getNamespace()).hasToString(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getEntityName()).hasToString(MAPPING_INCOMING_NAME);
        assertThat(adaptable.getPayload().getPath().toString()).hasToString(MAPPING_INCOMING_PATH);
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
                softly.assertThat(topicPath.getNamespace()).hasToString(MAPPING_INCOMING_NAMESPACE);
                softly.assertThat(topicPath.getEntityName()).hasToString(MAPPING_INCOMING_NAME);
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

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperPlain.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

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

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperBinary.map(message)).allSatisfy(adaptable -> {
            System.out.println(adaptable);

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

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperBinary.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

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
        final SubjectDeletionAnnouncement announcement =
                SubjectDeletionAnnouncement.of(policyId, Instant.now(), List.of(),
                        DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperChannelAsValue.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            System.out.println(
                    "testCorrectChannelForPolicyAnnouncementsJavascriptOutgoingMapping Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
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
                ConnectionOpenedAnnouncement.of(connectionId, Instant.now(),
                        DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(announcement);

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperChannelAsValue.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            System.out.println(
                    "testCorrectChannelForConnectionAnnouncementsJavascriptOutgoingMapping Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains(CONTENT_TYPE_PLAIN);
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isTrue();
            assertThat(rawMessage.isBytesMessage()).isFalse();
            assertThat(rawMessage.getTextPayload()).contains(TopicPath.Channel.NONE.getName());
        });
    }

    @Test
    public void testBinaryWithByteBufferJsJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE_BINARY);
        final BigInteger bigInteger = new BigInteger("27408B", 16);
        System.out.println(bigInteger);
        final byte[] bytes = bigInteger.toByteArray();
        System.out.println("bytes length: " + bytes.length);
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withBytes(bytes)
                .build();

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperBinaryWithByteBufferJs.map(message)).allSatisfy(adaptable -> {
            System.out.println(adaptable);

            System.out.println(
                    "testBinaryWithByteBufferJsJavascriptIncomingMapping Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
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
                assertThat(payload.getValue()).contains(JsonFactory.readFrom("{\"a\":11,\"b\":8,\"c\":99}"));
            });
        });
    }

    @Test
    public void testWithProtobufJsJavascriptIncomingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId);
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, "application/vnd.google.protobuf");
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withBytes(MAPPING_INCOMING_PROTOBUF_BYTES.duplicate())
                .build();

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperWithProtobufJs.map(message)).allSatisfy(adaptable -> {
            System.out.println(adaptable);

            System.out.println(
                    "testWithProtobufJsJavascriptIncomingMapping Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
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
                assertThat(payload.getValue()).contains(MAPPING_INCOMING_PROTOBUF_PARSED);
            });
        });
    }

    @Test
    public void testWithProtobufJsJavascriptOutgoingMapping() {
        final String correlationId = UUID.randomUUID().toString();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(true)
                .correlationId(correlationId)
                .contentType(ContentType.APPLICATION_JSON)
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
        final ThingId thingId = ThingId.of("org.eclipse.ditto.example:INTEGRATION_TEST_DEVICE");
        final String featureId = "SoftwareState";
        final Message<JsonValue> installMessage =
                Message.<JsonValue>newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, thingId, "install")
                                .featureId(featureId)
                                .contentType(ContentType.APPLICATION_JSON)
                                .build()
                        )
                        .payload(JsonFactory.readFrom(MAPPING_OUTGOING_PROTOBUF_VALUE))
                        .build();
        final SendFeatureMessage<JsonValue> sendInstallMessage = SendFeatureMessage.of(thingId, featureId,
                installMessage, dittoHeaders);
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(sendInstallMessage);

        final long startTs = System.nanoTime();
        assertThat(javaScriptRhinoMapperWithProtobufJs.map(adaptable)).allSatisfy(rawMessage -> {
            System.out.println(rawMessage);

            System.out.println(
                    "testWithProtobufJsJavascriptOutgoingMapping Duration: " +
                            (System.nanoTime() - startTs) / 1_000_000.0 +
                            "ms");

            assertThat(rawMessage.findContentType()).contains("application/vnd.google.protobuf");
            assertThat(rawMessage.findHeader(HEADER_CORRELATION_ID)).contains(correlationId);
            assertThat(rawMessage.isTextMessage()).isFalse();
            assertThat(rawMessage.isBytesMessage()).isTrue();
            assertThat(rawMessage.getTextPayload()).isEmpty();
            assertThat(rawMessage.getBytePayload())
                    .map(JavaScriptMessageMapperRhinoTest::byteBuffer2String)
                    .isNotEmpty();
        });
    }
}
