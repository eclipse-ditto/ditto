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
package org.eclipse.ditto.model.amqpbridge;

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
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAmqpConnection}.
 */
public final class ImmutableConnectionTest {

    private static final Pattern URI_PATTERN = Pattern.compile(AmqpConnection.UriRegex.REGEX);

    private static final String ID = "myConnection";

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;

    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationSubject AUTHORIZATION_SUBJECT =
            AuthorizationSubject.newInstance("mySolutionId:mySubject");

    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));

    private static final boolean FAILOVER_ENABLED = true;

    private static final boolean VALIDATE_CERTIFICATES = true;

    private static final int THROTTLE = 250;

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(AmqpConnection.JsonFields.ID, ID)
            .set(AmqpConnection.JsonFields.URI, URI)
            .set(AmqpConnection.JsonFields.AUTHORIZATION_SUBJECT, AUTHORIZATION_SUBJECT.getId())
            .set(AmqpConnection.JsonFields.SOURCES, SOURCES.stream()
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .set(AmqpConnection.JsonFields.FAILOVER_ENABLED, FAILOVER_ENABLED)
            .set(AmqpConnection.JsonFields.VALIDATE_CERTIFICATES, VALIDATE_CERTIFICATES)
            .set(AmqpConnection.JsonFields.THROTTLE, THROTTLE)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAmqpConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAmqpConnection.class, areImmutable(),
                provided(AuthorizationSubject.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableAmqpConnection.of(null, TYPE, URI, AUTHORIZATION_SUBJECT, SOURCES,
                        FAILOVER_ENABLED,
                        VALIDATE_CERTIFICATES, THROTTLE))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullUri() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableAmqpConnection.of(ID, TYPE, null, AUTHORIZATION_SUBJECT, SOURCES,
                        FAILOVER_ENABLED,
                        VALIDATE_CERTIFICATES, THROTTLE))
                .withMessage("The %s must not be null!", "URI")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullAuthorizationSubject() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableAmqpConnection.of(ID, TYPE, URI, null, SOURCES, FAILOVER_ENABLED,
                        VALIDATE_CERTIFICATES, THROTTLE))
                .withMessage("The %s must not be null!", "Authorization Subject")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullSources() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableAmqpConnection.of(ID, TYPE, URI, AUTHORIZATION_SUBJECT, null, FAILOVER_ENABLED,
                                VALIDATE_CERTIFICATES, THROTTLE))
                .withMessage("The %s must not be null!", "Sources")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ImmutableAmqpConnection expected =
                ImmutableAmqpConnection.of(ID, TYPE, URI, AUTHORIZATION_SUBJECT, SOURCES,
                        FAILOVER_ENABLED, VALIDATE_CERTIFICATES, THROTTLE);

        final ImmutableAmqpConnection actual = ImmutableAmqpConnection.fromJson(KNOWN_JSON);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                ImmutableAmqpConnection.of(ID, TYPE, URI, AUTHORIZATION_SUBJECT, SOURCES, FAILOVER_ENABLED,
                        VALIDATE_CERTIFICATES, THROTTLE).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void uriRegexMatchesExpected() {
        final Matcher matcher = URI_PATTERN.matcher("amqps://foo:bar@hono.eclipse.org:5671");

        final boolean matches = matcher.matches();

        assertThat(matches).isTrue();
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
