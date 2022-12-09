/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.model.HonoConnection}.
 */
public final class HonoConnectionTest {
    private static final ConnectionType TYPE = ConnectionType.HONO;
    private static final ConnectivityStatus STATUS = ConnectivityStatus.OPEN;

    private static final ConnectionId ID = ConnectionId.of("myHonoConnectionId");
    private static final String NAME = "myHonoConnection";

    private static final String URI_EMPTY = "";
    private static final Credentials CREDENTIALS = ClientCertificateCredentials.newBuilder().build();

    private static final AuthorizationContext AUTHORIZATION_CONTEXT =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                    AuthorizationSubject.newInstance("myIssuer:mySubject"));

    private static final String STATUS_MAPPING = "ConnectionStatus";
    private static final String JAVA_SCRIPT_MAPPING = "JavaScript";
    private static final String MIGRATED_MAPPER_ID = "javascript";

    private static final Source SOURCE1 = ConnectivityModelFactory.newSource(AUTHORIZATION_CONTEXT, "source");
    private static final Source SOURCE2 = ConnectivityModelFactory.newSource(AUTHORIZATION_CONTEXT, "source", 1);
    private static final List<Source> SOURCES = Arrays.asList(SOURCE1, SOURCE2);
    private static final List<Source> SOURCES_WITH_REPLY_TARGET_DISABLED = SOURCES.stream()
            .map(s -> ConnectivityModelFactory.newSourceBuilder(s).replyTargetEnabled(false).build())
            .collect(Collectors.toList());
    private static final HeaderMapping HEADER_MAPPING = ConnectivityModelFactory.emptyHeaderMapping();
    private static final Target TARGET1 = ConnectivityModelFactory.newTargetBuilder()
            .address("target")
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .headerMapping(HEADER_MAPPING)
            .topics(Topic.TWIN_EVENTS, Topic.LIVE_EVENTS)
            .build();
    private static final Target TARGET2 = ConnectivityModelFactory.newTargetBuilder()
            .address("target")
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .headerMapping(HEADER_MAPPING)
            .topics(Topic.LIVE_MESSAGES, Topic.LIVE_MESSAGES, Topic.LIVE_EVENTS)
            .build();
    private static final Target TARGET3 = ConnectivityModelFactory.newTargetBuilder()
            .address("target")
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .headerMapping(HEADER_MAPPING)
            .topics(Topic.LIVE_MESSAGES, Topic.LIVE_MESSAGES, Topic.LIVE_COMMANDS)
            .build();
    private static final List<Target> TARGETS = Arrays.asList(TARGET1, TARGET2, TARGET3);

    private static final JsonArray KNOWN_SOURCES_JSON =
            SOURCES.stream().map(Source::toJson).collect(JsonCollectors.valuesToArray());
    private static final JsonArray KNOWN_TARGETS_JSON =
            TARGETS.stream().map(Target::toJson).collect(JsonCollectors.valuesToArray());

    private static final JsonArray KNOWN_SOURCES_WITH_MAPPING_JSON =
            KNOWN_SOURCES_JSON.stream()
                    .map(JsonValue::asObject)
                    .map(o -> o.set(Source.JsonFields.PAYLOAD_MAPPING, JsonArray.of(JsonValue.of(JAVA_SCRIPT_MAPPING))))
                    .collect(JsonCollectors.valuesToArray());
    private static final JsonArray KNOWN_TARGETS_WITH_MAPPING_JSON =
            KNOWN_TARGETS_JSON.stream()
                    .map(JsonValue::asObject)
                    .map(o -> o.set(Source.JsonFields.PAYLOAD_MAPPING, JsonArray.of(JsonValue.of(STATUS_MAPPING))))
                    .collect(JsonCollectors.valuesToArray());
    private static final JsonArray KNOWN_SOURCES_WITH_REPLY_TARGET =
            KNOWN_SOURCES_WITH_MAPPING_JSON.stream()
                    .map(o -> o.asObject().toBuilder()
                            .set(Source.JsonFields.HEADER_MAPPING.getPointer(),
                                    ImmutableSource.DEFAULT_SOURCE_HEADER_MAPPING.toJson())
                            .set(Source.JsonFields.REPLY_TARGET.getPointer(),
                                    ReplyTarget.newBuilder().address(ImmutableSource.DEFAULT_REPLY_TARGET_ADDRESS)
                                            .headerMapping(ImmutableSource.DEFAULT_REPLY_TARGET_HEADER_MAPPING)
                                            .build()
                                            .toJson())
                            .set(Source.JsonFields.REPLY_TARGET_ENABLED, true)
                            .build())
                    .collect(JsonCollectors.valuesToArray());
    private static final JsonArray KNOWN_TARGETS_WITH_HEADER_MAPPING =
            KNOWN_TARGETS_WITH_MAPPING_JSON.stream()
                    .map(o -> o.asObject().toBuilder()
                            .set(Target.JsonFields.HEADER_MAPPING, o.asObject()
                                    .getValue(Target.JsonFields.HEADER_MAPPING)
                                    .orElseGet(ConnectivityModelFactory.emptyHeaderMapping()::toJson))
                            .build())
                    .collect(JsonCollectors.valuesToArray());

    private static final MappingContext KNOWN_MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext(
            JAVA_SCRIPT_MAPPING,
            Collections.singletonMap("incomingScript",
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
                            "    let name = \"foo-bar\";\n" +
                            "    let group = \"things\";\n" +
                            "    let channel = \"twin\";\n" +
                            "    let criterion = \"commands\";\n" +
                            "    let action = \"modify\";\n" +
                            "    let path = \"/attributes/foo\";\n" +
                            "    let dittoHeaders = headers;\n" +
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
                            "}"));

    private static final MappingContext KNOWN_JAVA_MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext(
            STATUS_MAPPING, new HashMap<>());

    private static final PayloadMappingDefinition KNOWN_MAPPING_DEFINITIONS =
            ConnectivityModelFactory.newPayloadMappingDefinition(
                    Stream.of(KNOWN_MAPPING_CONTEXT, KNOWN_JAVA_MAPPING_CONTEXT)
                            .collect(Collectors.toMap(MappingContext::getMappingEngine, ctx -> ctx)));

    private static final PayloadMappingDefinition LEGACY_MAPPINGS =
            ConnectivityModelFactory.newPayloadMappingDefinition(
                    Stream.of(KNOWN_MAPPING_CONTEXT).collect(Collectors.toMap(ctx -> MIGRATED_MAPPER_ID, ctx -> ctx)));

    private static final Set<String> KNOWN_TAGS = Collections.singleton("HONO");

    private static final JsonObject KNOWN_JSON_WITHOUT_URI= JsonObject.newBuilder()
            .set(Connection.JsonFields.ID, ID.toString())
            .set(Connection.JsonFields.NAME, NAME)
            .set(Connection.JsonFields.CONNECTION_TYPE, TYPE.getName())
            .set(Connection.JsonFields.CONNECTION_STATUS, STATUS.getName())
            .set(Connection.JsonFields.CREDENTIALS, CREDENTIALS.toJson())
            .set(Connection.JsonFields.SOURCES, KNOWN_SOURCES_WITH_MAPPING_JSON)
            .set(Connection.JsonFields.TARGETS, KNOWN_TARGETS_WITH_MAPPING_JSON)
            .set(Connection.JsonFields.CLIENT_COUNT, 2)
            .set(Connection.JsonFields.FAILOVER_ENABLED, true)
            .set(Connection.JsonFields.VALIDATE_CERTIFICATES, true)
            .set(Connection.JsonFields.PROCESSOR_POOL_SIZE, 1)
            .set(Connection.JsonFields.MAPPING_DEFINITIONS,
                    JsonObject.newBuilder()
                            .set(JAVA_SCRIPT_MAPPING, KNOWN_MAPPING_CONTEXT.toJson())
                            .set(STATUS_MAPPING, KNOWN_JAVA_MAPPING_CONTEXT.toJson())
                            .build())
            .set(Connection.JsonFields.TAGS, KNOWN_TAGS.stream()
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
             .build();

    private final static JsonObject KNOWN_JSON_WITH_EMPTY_URI = KNOWN_JSON_WITHOUT_URI.set(Connection.JsonFields.URI, "");

    private final static JsonObject KNOWN_JSON_WITH_NULL_URI = KNOWN_JSON_WITHOUT_URI.set(Connection.JsonFields.URI, null);

    private static final JsonObject KNOWN_JSON_WITH_REPLY_TARGET = KNOWN_JSON_WITH_EMPTY_URI
            .set(Connection.JsonFields.SOURCES, KNOWN_SOURCES_WITH_REPLY_TARGET)
            .set(Connection.JsonFields.TARGETS, KNOWN_TARGETS_WITH_HEADER_MAPPING);

    private static final JsonObject KNOWN_LEGACY_JSON = KNOWN_JSON_WITH_EMPTY_URI
            .set(Connection.JsonFields.MAPPING_CONTEXT, KNOWN_MAPPING_CONTEXT.toJson());

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(HonoConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableConnection.class, areImmutable(),
                provided(AuthorizationContext.class, Source.class, Target.class,
                        MappingContext.class, Credentials.class, ConnectionId.class,
                        PayloadMappingDefinition.class, SshTunnel.class).isAlsoImmutable(),
                assumingFields("mappings").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void createMinimalConnectionConfigurationInstance() {
        Connection connection = HonoConnection.fromJson(KNOWN_JSON_WITHOUT_URI);
        connection = connection.toBuilder().setSources(SOURCES_WITH_REPLY_TARGET_DISABLED).build();
        assertThat((CharSequence) connection.getId()).isEqualTo(ID);
        assertThat((Object) connection.getConnectionType()).isEqualTo(TYPE);
        assertThat(connection.getUri()).isEqualTo(URI_EMPTY);
        assertThat(connection.getSources()).isEqualTo(SOURCES_WITH_REPLY_TARGET_DISABLED);
    }

    @Test
    public void createMinimalConnectionConfigurationInstanceWithEmptyUri() {
        Connection connection = HonoConnection.fromJson(KNOWN_JSON_WITH_EMPTY_URI);
        connection = connection.toBuilder().setSources(SOURCES_WITH_REPLY_TARGET_DISABLED).build();
        assertThat((CharSequence) connection.getId()).isEqualTo(ID);
        assertThat((Object) connection.getConnectionType()).isEqualTo(TYPE);
        assertThat(connection.getUri()).isEqualTo(URI_EMPTY);
        assertThat(connection.getSources()).isEqualTo(SOURCES_WITH_REPLY_TARGET_DISABLED);
    }

    @Test
    public void createInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectivityModelFactory.newConnectionBuilder(null, TYPE, STATUS, URI_EMPTY))
                .withMessage("The %s must not be null!", "id")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullUri() {
        Connection connection = HonoConnection.fromJson(KNOWN_JSON_WITH_NULL_URI);
        connection = connection.toBuilder().setSources(SOURCES_WITH_REPLY_TARGET_DISABLED).build();
        assertThat(connection.getUri()).isEmpty();
    }

    @Test
    public void createInstanceWithEmptyUri() {
        Connection connection = HonoConnection.fromJson(KNOWN_JSON_WITH_EMPTY_URI);
        assertThat(connection.getUri()).isEmpty();
    }

    @Test
    public void getBuilderFromConnectionCoversAllFields() {

        final Connection connection = HonoConnection.getBuilder(ID, TYPE, STATUS, URI_EMPTY)
                .sources(SOURCES)
                .targets(TARGETS)
                .connectionStatus(ConnectivityStatus.OPEN)
                .name("connection")
                .clientCount(5)
                .tag("AAA")
                .trustedCertificates("certs")
                .processorPoolSize(8)
                .credentials(ClientCertificateCredentials.newBuilder()
                        .clientKey("clientkey")
                        .clientCertificate("certificate")
                        .build())
                .validateCertificate(true)
                //.uri(null)
                .id(ID)
                .payloadMappingDefinition(
                        ConnectivityModelFactory.newPayloadMappingDefinition("test", KNOWN_JAVA_MAPPING_CONTEXT))
                .build();
        assertThat(HonoConnection.getBuilder(connection).build()).isEqualTo(connection);
        assertThat(HonoConnection.getBuilder(connection).build().getUri()).isEqualTo(URI_EMPTY);
    }

    @Test
    public void createInstanceWithNullSources() {
        final ConnectionBuilder builder = HonoConnection.getBuilder(ID, TYPE, STATUS, URI_EMPTY);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.sources(null))
                .withMessage("The %s must not be null!", "sources")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullEventTarget() {
        final ConnectionBuilder builder = HonoConnection.getBuilder(ID, TYPE, STATUS, URI_EMPTY);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.targets(null))
                .withMessage("The %s must not be null!", "targets")
                .withNoCause();
    }

    @Test
    public void createHonoWhitInvalidConnectionType() {
       final Connection connection = HonoConnection.getBuilder(ID, ConnectionType.AMQP_10, STATUS, URI_EMPTY)
                .sources(SOURCES)
                .targets(TARGETS)
                .connectionStatus(ConnectivityStatus.OPEN)
                .name("connection")
                .clientCount(5)
                .trustedCertificates("certs")
                .processorPoolSize(8)
                .id(ID)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> HonoConnection.getConnectionTypeOrThrow(connection.toJson()))
                .withMessage("Connection type <%s> is invalid! Connection type must be of type <%s>.",
                        ConnectionType.AMQP_10.getName(), ConnectionType.HONO.getName())
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullSourcesAndEmptyUri() {
        final ConnectionBuilder builder = HonoConnection.getBuilder(ID, TYPE, STATUS, URI_EMPTY);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.sources(null))
                .withMessage("The %s must not be null!", "sources")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullEventTargetAndEmptyUri() {
        final ConnectionBuilder builder = HonoConnection.getBuilder(ID, TYPE, STATUS, URI_EMPTY);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.targets(null))
                .withMessage("The %s must not be null!", "targets")
                .withNoCause();
    }

    @Test
    public void createInstanceWithConnectionAnnouncementsAndClientCountGreater1() {
        final ConnectionBuilder builder = HonoConnection.getBuilder(ID, TYPE, STATUS, URI_EMPTY)
                .targets(Collections.singletonList(
                        ConnectivityModelFactory.newTargetBuilder(TARGET1)
                                .topics(Topic.CONNECTION_ANNOUNCEMENTS).build())
                )
                .clientCount(2);
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(builder::build)
                .withMessageContaining(Topic.CONNECTION_ANNOUNCEMENTS.getName())
                .withNoCause();
    }

    @Test
    public void fromJsonWithLegacyMappingContextReturnsExpected() {
        final Map<String, MappingContext> definitions = new HashMap<>(KNOWN_MAPPING_DEFINITIONS.getDefinitions());
        definitions.putAll(LEGACY_MAPPINGS.getDefinitions());
        final Connection expected = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI_EMPTY)
                .credentials(CREDENTIALS)
                .name(NAME)
                .setSources(addSourceMapping(SOURCES, JAVA_SCRIPT_MAPPING, "javascript"))
                .setTargets(addTargetMapping(TARGETS, STATUS_MAPPING, "javascript"))
                .clientCount(2)
                .payloadMappingDefinition(ConnectivityModelFactory.newPayloadMappingDefinition(definitions))
                .tags(KNOWN_TAGS)
                .build();

        final Connection actual = HonoConnection.fromJson(KNOWN_LEGACY_JSON);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromInvalidJsonFails() {
        final JsonObject INVALID_JSON = KNOWN_JSON_WITHOUT_URI.remove(Connection.JsonFields.SOURCES.getPointer())
                .remove(Connection.JsonFields.TARGETS.getPointer());

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> HonoConnection.fromJson(INVALID_JSON))
                .withMessageContaining("source")
                .withMessageContaining("target")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Connection expected = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI_EMPTY)
                .credentials(CREDENTIALS)
                .name(NAME)
                .setSources(addSourceMapping(SOURCES, JAVA_SCRIPT_MAPPING))
                .setTargets(addTargetMapping(TARGETS, STATUS_MAPPING))
                .clientCount(2)
                .payloadMappingDefinition(KNOWN_MAPPING_DEFINITIONS)
                .tags(KNOWN_TAGS)
                .build();

        final Connection actual = HonoConnection.fromJson(KNOWN_JSON_WITHOUT_URI);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final Connection underTest = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI_EMPTY)
                .credentials(CREDENTIALS)
                .name(NAME)
                .sources(addSourceMapping(Arrays.asList(SOURCE2, SOURCE1),
                        JAVA_SCRIPT_MAPPING)) // use different order to test sorting
                .targets(addTargetMapping(TARGETS, STATUS_MAPPING))
                .clientCount(2)
                .payloadMappingDefinition(KNOWN_MAPPING_DEFINITIONS)
                .tags(KNOWN_TAGS)
                .build();

        final JsonObject actual = underTest.toJson();
        assertThat(actual).isEqualTo(KNOWN_JSON_WITH_REPLY_TARGET);
    }

    @Test
    public void emptyCertificatesLeadToEmptyOptional() {
        final Connection underTest = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI_EMPTY)
                .targets(TARGETS)
                .validateCertificate(true)
                .trustedCertificates("")
                .build();

        assertThat(underTest.getTrustedCertificates()).isEmpty();
    }

    @Test
    public void emptyCertificatesFromJsonLeadToEmptyOptional() {
        final JsonObject connectionJsonWithEmptyCa = KNOWN_JSON_WITHOUT_URI
                .set(Connection.JsonFields.VALIDATE_CERTIFICATES, true)
                .set(Connection.JsonFields.TRUSTED_CERTIFICATES, "");
        final Connection underTest = ConnectivityModelFactory.connectionFromJson(connectionJsonWithEmptyCa);

        assertThat(underTest.getTrustedCertificates()).isEmpty();
    }

    private List<Source> addSourceMapping(final List<Source> sources, final String... mapping) {
        return sources.stream()
                .map(s -> new ImmutableSource.Builder(s).payloadMapping(
                        ConnectivityModelFactory.newPayloadMapping(mapping)).build())
                .collect(Collectors.toList());
    }

    private List<Target> addTargetMapping(final List<Target> targets, final String... mapping) {
        return targets.stream()
                .map(t -> new ImmutableTarget.Builder(t).payloadMapping(
                        ConnectivityModelFactory.newPayloadMapping(mapping)).build())
                .collect(Collectors.toList());
    }

    @Test
    public void providesDefaultHeaderMappings() {

        final Target targetWithoutHeaderMapping = ConnectivityModelFactory.newTargetBuilder()
                .address("amqp/target1")
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .topics(Topic.TWIN_EVENTS)
                .build();
        final Connection connectionWithoutHeaderMappingForTarget =
                ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI_EMPTY)
                        .targets(Collections.singletonList(targetWithoutHeaderMapping))
                        .build();

        connectionWithoutHeaderMappingForTarget.getTargets()
                .forEach(target -> assertThat(target.getHeaderMapping())
                        .isEqualTo(ConnectivityModelFactory.emptyHeaderMapping()));
    }

    @Test
    public void providesDefaultHeaderMappingsFromJson() {
        final JsonObject connectionJsonWithoutHeaderMappingForTarget = KNOWN_JSON_WITHOUT_URI
                .set(Connection.JsonFields.TARGETS, JsonArray.of(
                        TARGET1.toJson()
                                .remove(Target.JsonFields.HEADER_MAPPING.getPointer())));
        final Connection connectionWithoutHeaderMappingForTarget =
                HonoConnection.fromJson(connectionJsonWithoutHeaderMappingForTarget);

        connectionWithoutHeaderMappingForTarget.getTargets()
                .forEach(target -> assertThat(target.getHeaderMapping())
                        .isEqualTo(ConnectivityModelFactory.emptyHeaderMapping()));
    }

}
