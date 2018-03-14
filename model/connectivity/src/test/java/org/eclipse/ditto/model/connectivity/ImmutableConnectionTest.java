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
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String ID = "myConnection";

    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));
    private static final String TARGET = "eventQueue";

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Connection.JsonFields.ID, ID)
            .set(Connection.JsonFields.CONNECTION_TYPE, TYPE.getName())
            .set(Connection.JsonFields.URI, URI)
            .set(Connection.JsonFields.AUTHORIZATION_CONTEXT, AUTHORIZATION_CONTEXT.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .set(Connection.JsonFields.SOURCES, SOURCES.stream()
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .set(Connection.JsonFields.EVENT_TARGET, TARGET)
            .set(Connection.JsonFields.FAILOVER_ENABLED, true)
            .set(Connection.JsonFields.VALIDATE_CERTIFICATES, true)
            .set(Connection.JsonFields.THROTTLE, -1)
            .set(Connection.JsonFields.CONSUMER_COUNT, 1)
            .set(Connection.JsonFields.PROCESSOR_POOL_SIZE, 5)
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
                provided(AuthorizationContext.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableConnection.of(null, TYPE, URI, AUTHORIZATION_CONTEXT))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullUri() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableConnection.of(ID, TYPE, null, AUTHORIZATION_CONTEXT))
                .withMessage("The %s must not be null!", "URI")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullAuthorizationSubject() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableConnection.of(ID, TYPE, URI, null))
                .withMessage("The %s must not be null!", "Authorization Context")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullSources() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableConnectionBuilder.of(ID, TYPE, URI, AUTHORIZATION_CONTEXT)
                                .sources((Set) null))
                .withMessage("The %s must not be null!", "Sources")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullEventTarget() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableConnectionBuilder.of(ID, TYPE, URI, AUTHORIZATION_CONTEXT).eventTarget(null))
                .withMessage("The %s must not be null!", "eventTarget")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullReplyTarget() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableConnectionBuilder.of(ID, TYPE, URI, AUTHORIZATION_CONTEXT).replyTarget(null))
                .withMessage("The %s must not be null!", "replyTarget")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Connection expected =
                ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, URI, AUTHORIZATION_CONTEXT)
                        .sources(SOURCES)
                        .eventTarget(TARGET)
                        .build();

        final Connection actual = ImmutableConnection.fromJson(KNOWN_JSON);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, URI, AUTHORIZATION_CONTEXT)
                        .sources(SOURCES)
                        .eventTarget(TARGET)
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
