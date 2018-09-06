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
package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.connectivity.ImmutableConnection.ConnectionUri;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableConnection}.
 */
public final class ImmutableConnectionTest {

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectionStatus STATUS = ConnectionStatus.OPEN;

    private static final String ID = "myConnectionId";
    private static final String NAME = "myConnection";

    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    private static final Source SOURCE1 = ConnectivityModelFactory.newSource(0, AUTHORIZATION_CONTEXT, "amqp/source1");
    private static final Source SOURCE2 =
            ConnectivityModelFactory.newSource(1, 1, AUTHORIZATION_CONTEXT, "amqp/source2");
    private static final List<Source> SOURCES = Arrays.asList(SOURCE1, SOURCE2);
    private static final Target TARGET1 =
            ConnectivityModelFactory.newTarget("amqp/target1", AUTHORIZATION_CONTEXT, Topic.TWIN_EVENTS,
                    Topic.LIVE_EVENTS);
    private static final Target TARGET2 =
            ConnectivityModelFactory.newTarget("amqp/target2", AUTHORIZATION_CONTEXT, Topic.LIVE_MESSAGES,
                    Topic.LIVE_MESSAGES,
                    Topic.LIVE_EVENTS);
    private static final Target TARGET3 =
            ConnectivityModelFactory.newTarget("amqp/target3", AUTHORIZATION_CONTEXT, Topic.LIVE_MESSAGES,
                    Topic.LIVE_MESSAGES, Topic.LIVE_COMMANDS);
    private static final Set<Target> TARGETS = new HashSet<>(Arrays.asList(TARGET1, TARGET2, TARGET3));

    private static final JsonArray KNOWN_SOURCES_JSON =
            SOURCES.stream().map(Source::toJson).collect(JsonCollectors.valuesToArray());
    private static final JsonArray KNOWN_TARGETS_JSON =
            TARGETS.stream().map(Target::toJson).collect(JsonCollectors.valuesToArray());

    private static final MappingContext KNOWN_MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext(
            "JavaScript",
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
                            "    let id = \"foo-bar\";\n" +
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
                            "        id,\n" +
                            "        group,\n" +
                            "        channel,\n" +
                            "        criterion,\n" +
                            "        action,\n" +
                            "        path,\n" +
                            "        dittoHeaders,\n" +
                            "        value\n" +
                            "    );\n" +
                            "}"));

    private static final Set<String> KNOWN_TAGS = Collections.singleton("HONO");

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Connection.JsonFields.ID, ID)
            .set(Connection.JsonFields.NAME, NAME)
            .set(Connection.JsonFields.CONNECTION_TYPE, TYPE.getName())
            .set(Connection.JsonFields.CONNECTION_STATUS, STATUS.getName())
            .set(Connection.JsonFields.URI, URI)
            .set(Connection.JsonFields.SOURCES, KNOWN_SOURCES_JSON)
            .set(Connection.JsonFields.TARGETS, KNOWN_TARGETS_JSON)
            .set(Connection.JsonFields.CLIENT_COUNT, 2)
            .set(Connection.JsonFields.FAILOVER_ENABLED, true)
            .set(Connection.JsonFields.VALIDATE_CERTIFICATES, true)
            .set(Connection.JsonFields.PROCESSOR_POOL_SIZE, 5)
            .set(Connection.JsonFields.MAPPING_CONTEXT, KNOWN_MAPPING_CONTEXT.toJson())
            .set(Connection.JsonFields.TAGS, KNOWN_TAGS.stream()
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableConnection.class, areImmutable(),
                provided(AuthorizationContext.class, Source.class, Target.class,
                        MappingContext.class).isAlsoImmutable());
    }

    @Test
    public void createMinimalConnectionConfigurationInstance() {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI)
                .sources(SOURCES)
                .targets(TARGETS)
                .build();

        assertThat(connection.getId()).isEqualTo(ID);
        assertThat((Object) connection.getConnectionType()).isEqualTo(TYPE);
        assertThat(connection.getUri()).isEqualTo(URI);
        assertThat(connection.getSources()).isEqualTo(SOURCES);
    }

    @Test
    public void createInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectivityModelFactory.newConnectionBuilder(null, TYPE, STATUS, URI))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullUri() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, null))
                .withMessage("The %s must not be null!", "URI")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullSources() {
        final ConnectionBuilder builder = ImmutableConnection.getBuilder(ID, TYPE, STATUS, URI);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.sources(null))
                .withMessage("The %s must not be null!", "sources")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullEventTarget() {
        final ConnectionBuilder builder = ImmutableConnection.getBuilder(ID, TYPE, STATUS, URI);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.targets(null))
                .withMessage("The %s must not be null!", "targets")
                .withNoCause();
    }

    @Test
    public void createInstanceWithoutSourceAndEventTarget() {
        final ConnectionBuilder builder = ImmutableConnection.getBuilder(ID, TYPE, STATUS, URI);

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(builder::build)
                .withMessageContaining("source")
                .withMessageContaining("target")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Connection expected = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI)
                .name(NAME)
                .sources(SOURCES)
                .targets(TARGETS)
                .clientCount(2)
                .mappingContext(KNOWN_MAPPING_CONTEXT)
                .tags(KNOWN_TAGS)
                .build();

        final Connection actual = ImmutableConnection.fromJson(KNOWN_JSON);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromInvalidJsonFails() {
        final JsonObject INVALID_JSON = KNOWN_JSON.remove(Connection.JsonFields.SOURCES.getPointer())
                .remove(Connection.JsonFields.TARGETS.getPointer());

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> ImmutableConnection.fromJson(INVALID_JSON))
                .withMessageContaining("source")
                .withMessageContaining("target")
                .withNoCause();
    }

    @Test
    public void toJsonReturnsExpected() {
        final Connection underTest = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI)
                .name(NAME)
                .sources(Arrays.asList(SOURCE2, SOURCE1)) // use different order to test sorting
                .targets(TARGETS)
                .clientCount(2)
                .mappingContext(KNOWN_MAPPING_CONTEXT)
                .tags(KNOWN_TAGS)
                .build();

        final JsonObject actual = underTest.toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void parseUriAsExpected() {
        final ConnectionUri underTest = ConnectionUri.of("amqps://foo:bar@hono.eclipse.org:5671/vhost");

        assertThat(underTest.getProtocol()).isEqualTo("amqps");
        assertThat(underTest.getUserName()).contains("foo");
        assertThat(underTest.getPassword()).contains("bar");
        assertThat(underTest.getHostname()).isEqualTo("hono.eclipse.org");
        assertThat(underTest.getPort()).isEqualTo(5671);
        assertThat(underTest.getPath()).contains("/vhost");
    }

    @Test
    public void parseUriWithoutCredentials() {
        final ConnectionUri underTest = ConnectionUri.of("amqps://hono.eclipse.org:5671");

        assertThat(underTest.getUserName()).isEmpty();
        assertThat(underTest.getPassword()).isEmpty();
    }

    @Test
    public void parseUriWithoutPath() {
        final ConnectionUri underTest = ConnectionUri.of("amqps://foo:bar@hono.eclipse.org:5671");

        assertThat(underTest.getPath()).isEmpty();
    }

    @Test(expected = ConnectionUriInvalidException.class)
    public void cannotParseUriWithoutPort() {
        ConnectionUri.of("amqps://foo:bar@hono.eclipse.org");
    }

    @Test(expected = ConnectionUriInvalidException.class)
    public void cannotParseUriWithoutHost() {
        ConnectionUri.of("amqps://foo:bar@:5671");
    }


    /**
     * Permit construction of connection URIs with username and without password
     * because RFC-3986 permits it.
     */
    @Test
    public void canParseUriWithUsernameWithoutPassword() {
        final ConnectionUri underTest = ConnectionUri.of("amqps://foo:@hono.eclipse.org:5671");

        assertThat(underTest.getUserName()).contains("foo");
        assertThat(underTest.getPassword()).contains("");
    }

    @Test
    public void canParseUriWithoutUsernameWithPassword() {
        final ConnectionUri underTest = ConnectionUri.of("amqps://:bar@hono.eclipse.org:5671");

        assertThat(underTest.getUserName()).contains("");
        assertThat(underTest.getPassword()).contains("bar");
    }

    @Test(expected = ConnectionUriInvalidException.class)
    public void uriRegexFailsWithoutProtocol() {
        ConnectionUri.of("://foo:bar@hono.eclipse.org:5671");
    }

    @Test
    public void toStringDoesNotContainPassword() {
        final String password = "thePassword";

        final String uri = "amqps://foo:" + password + "@host.com:5671";

        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, uri)
                .sources(Collections.singletonList(SOURCE1))
                .build();

        assertThat(connection.toString()).doesNotContain(password);
    }

}
