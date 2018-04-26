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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern URI_PATTERN = Pattern.compile(Connection.UriRegex.REGEX);

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectionStatus STATUS = ConnectionStatus.OPEN;

    private static final String ID = "myConnection";

    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    private static final Source SOURCE1 = ImmutableSource.of("amqp/source1");
    private static final Source SOURCE2 = ImmutableSource.of("amqp/source2");
    private static final Set<Source> SOURCES = new HashSet<>(Arrays.asList(SOURCE1, SOURCE2));
    private static final Target TARGET1 =
            ImmutableTarget.of("amqp/target1", "_/_/things/twin/events", "_/_/things/live/events");
    private static final Target TARGET2 =
            ImmutableTarget.of("amqp/target2", "_/_/things/live/messages", "_/_/things/live/messages",
                    "_/_/things/live/events");
    private static final Target TARGET3 =
            ImmutableTarget.of("amqp/target3", "_/_/things/live/messages", "_/_/things/live/messages",
                    "_/_/things/live/commands");
    private static final Set<Target> TARGETS = new HashSet<>(Arrays.asList(TARGET1, TARGET2, TARGET3));

    private static final JsonArray KNOWN_SOURCES_JSON =
            SOURCES.stream().map(Source::toJson).collect(JsonCollectors.valuesToArray());
    private static final JsonArray KNOWN_TARGETS_JSON =
            TARGETS.stream().map(Target::toJson).collect(JsonCollectors.valuesToArray());

    private static MappingContext KNOWN_MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext(
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

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Connection.JsonFields.ID, ID)
            .set(Connection.JsonFields.CONNECTION_TYPE, TYPE.getName())
            .set(Connection.JsonFields.CONNECTION_STATUS, STATUS.getName())
            .set(Connection.JsonFields.URI, URI)
            .set(Connection.JsonFields.AUTHORIZATION_CONTEXT, AUTHORIZATION_CONTEXT.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .set(Connection.JsonFields.SOURCES, KNOWN_SOURCES_JSON)
            .set(Connection.JsonFields.TARGETS, KNOWN_TARGETS_JSON)
            .set(Connection.JsonFields.CLIENT_COUNT, 2)
            .set(Connection.JsonFields.FAILOVER_ENABLED, true)
            .set(Connection.JsonFields.VALIDATE_CERTIFICATES, true)
            .set(Connection.JsonFields.PROCESSOR_POOL_SIZE, 5)
            .set(Connection.JsonFields.MAPPING_CONTEXT, KNOWN_MAPPING_CONTEXT.toJson())
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
                provided(AuthorizationContext.class, Source.class, Target.class, MappingContext.class).isAlsoImmutable());
    }

    @Test
    public void createMinimalConnectionConfigurationInstance() {
        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT)
                .sources(SOURCES)
                .targets(TARGETS)
                .build();

        assertThat(connection.getId()).isEqualTo(ID);
        assertThat((Object) connection.getConnectionType()).isEqualTo(TYPE);
        assertThat(connection.getUri()).isEqualTo(URI);
        assertThat(connection.getAuthorizationContext()).isEqualTo(AUTHORIZATION_CONTEXT);
        assertThat(connection.getSources()).isEqualTo(SOURCES);
    }

    @Test
    public void createInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectivityModelFactory.newConnectionBuilder(null, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullUri() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, null, AUTHORIZATION_CONTEXT))
                .withMessage("The %s must not be null!", "URI")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullAuthorizationSubject() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI, null))
                .withMessage("The %s must not be null!", "Authorization Context")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullSources() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableConnectionBuilder.of(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT)
                                .sources((Set) null))
                .withMessage("The %s must not be null!", "Sources")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullEventTarget() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableConnectionBuilder.of(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT).targets(null))
                .withMessage("The %s must not be null!", "Targets")
                .withNoCause();
    }

    @Test
    public void createInstanceWithoutSourceAndEventTarget() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(
                        () -> ImmutableConnectionBuilder.of(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT).build())
                .withMessageContaining("source")
                .withMessageContaining("target")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Connection expected =
                ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT)
                        .sources(SOURCES)
                        .targets(TARGETS)
                        .clientCount(2)
                        .mappingContext(KNOWN_MAPPING_CONTEXT)
                        .build();

        final Connection actual = ImmutableConnection.fromJson(KNOWN_JSON);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromInvalidJsonFails() {
        final JsonObject INVALID_JSON = KNOWN_JSON.remove(Connection.JsonFields.SOURCES.getPointer())
                .remove(Connection.JsonFields.TARGETS.getPointer());

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(
                        () -> ImmutableConnection.fromJson(INVALID_JSON))
                .withMessageContaining("source")
                .withMessageContaining("target")
                .withNoCause();
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT)
                        .sources(SOURCES)
                        .targets(TARGETS)
                        .clientCount(2)
                        .mappingContext(KNOWN_MAPPING_CONTEXT)
                        .build()
                        .toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void uriRegexMatchesExpected() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://foo:bar@hono.eclipse.org:5671/vhost");

        final boolean matches = matcher.matches();

        assertThat(matches).isTrue();
        assertThat(matcher.group(Connection.UriRegex.PROTOCOL_REGEX_GROUP)).isEqualTo("amqps");
        assertThat(matcher.group(Connection.UriRegex.USERNAME_REGEX_GROUP)).isEqualTo("foo");
        assertThat(matcher.group(Connection.UriRegex.PASSWORD_REGEX_GROUP)).isEqualTo("bar");
        assertThat(matcher.group(Connection.UriRegex.HOSTNAME_REGEX_GROUP)).isEqualTo("hono.eclipse.org");
        assertThat(matcher.group(Connection.UriRegex.PORT_REGEX_GROUP)).isEqualTo("5671");
        assertThat(matcher.group(Connection.UriRegex.PATH_REGEX_GROUP)).isEqualTo("vhost");
    }

    @Test
    public void uriRegexMatchesWithoutCredentials() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://hono.eclipse.org:5671");

        final boolean matches = matcher.matches();
        assertThat(matches).isTrue();
        assertThat(matcher.group(Connection.UriRegex.USERNAME_REGEX_GROUP)).isNull();
        assertThat(matcher.group(Connection.UriRegex.PASSWORD_REGEX_GROUP)).isNull();
    }

    @Test
    public void uriRegexMatchesWithoutVHost() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://foo:bar@hono.eclipse.org:5671");

        final boolean matches = matcher.matches();
        assertThat(matches).isTrue();
        assertThat(matcher.group(Connection.UriRegex.PATH_REGEX_GROUP)).isNull();
    }

    @Test
    public void uriRegexFailsWithoutPort() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://foo:bar@hono.eclipse.org");

        final boolean matches = matcher.matches();

        assertThat(matches).isFalse();
    }

    @Test
    public void uriRegexFailsWithoutHost() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://foo:bar@:5671");

        final boolean matches = matcher.matches();

        assertThat(matches).isFalse();
    }

    @Test
    public void uriRegexFailsWithoutPassword() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://foo:@hono.eclipse.org:5671");

        final boolean matches = matcher.matches();

        assertThat(matches).isFalse();
    }

    @Test
    public void uriRegexFailsWithoutUsername() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://:bar@hono.eclipse.org:5671");

        final boolean matches = matcher.matches();

        assertThat(matches).isFalse();
    }

    @Test
    public void uriRegexFailsWithoutProtocol() {
        final Matcher matcher = URI_PATTERN.matcher("://foo:bar@hono.eclipse.org:5671");

        final boolean matches = matcher.matches();

        assertThat(matches).isFalse();
    }

    @Test
    public void uriRegexFailsWithOtherThanAmqpProtocol() {
        final Matcher matcher = URI_PATTERN.matcher("http://foo:bar@hono.eclipse.org:5671");

        final boolean matches = matcher.matches();

        assertThat(matches).isFalse();
    }

}
