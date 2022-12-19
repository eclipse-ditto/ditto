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

import org.junit.Test;

public final class ConnectionUriTest {

    @Test
    public void parseUriAsExpected() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://foo:bar@hono.eclipse.org:5671/vhost");

        assertThat(underTest.getProtocol()).isEqualTo("amqps");
        assertThat(underTest.getUserName()).contains("foo");
        assertThat(underTest.getPassword()).contains("bar");
        assertThat(underTest.getHostname()).isEqualTo("hono.eclipse.org");
        assertThat(underTest.getPort()).isEqualTo(5671);
        assertThat(underTest.getPath()).contains("/vhost");
    }

    @Test
    public void parsePasswordWithPlusSign() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://foo:bar+baz@hono.eclipse.org:5671/vhost");
        assertThat(underTest.getPassword()).contains("bar+baz");
    }

    @Test
    public void parsePasswordWithPlusSignEncoded() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://foo:bar%2Bbaz@hono.eclipse.org:5671/vhost");
        assertThat(underTest.getPassword()).contains("bar+baz");
    }

    @Test
    public void parsePasswordWithPlusSignDoubleEncoded() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://foo:bar%252Bbaz@hono.eclipse.org:5671/vhost");
        assertThat(underTest.getPassword()).contains("bar%2Bbaz");
    }

    @Test
    public void parseUriWithoutCredentials() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://hono.eclipse.org:5671");

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
     * Permit construction of connection URIs with username and without password because RFC-3986 permits it.
     */
    @Test
    public void canParseUriWithUsernameWithoutPassword() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://foo:@hono.eclipse.org:5671");

        assertThat(underTest.getUserName()).contains("foo");
        assertThat(underTest.getPassword()).contains("");
    }

    @Test
    public void canParseUriWithoutUsernameWithPassword() {
        final ConnectionUri underTest =
                ConnectionUri.of("amqps://:bar@hono.eclipse.org:5671");

        assertThat(underTest.getUserName()).contains("");
        assertThat(underTest.getPassword()).contains("bar");
    }

    @Test(expected = ConnectionUriInvalidException.class)
    public void uriRegexFailsWithoutProtocol() {
        ConnectionUri.of("://foo:bar@hono.eclipse.org:5671");
    }

    @Test
    public void testPasswordFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPassword()).isEmpty();
    }

    @Test
    public void testUserFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getUserName()).isEmpty();
    }

    @Test
    public void testPortFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPort()).isEqualTo(9999);
    }

    @Test
    public void testHostFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getHostname()).isEmpty();
    }

    @Test
    public void testProtocolFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPassword()).isEmpty();
    }

    @Test
    public void testPathFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPath()).isEmpty();
    }

    @Test
    public void testUriStringWithMaskedPasswordFromUriWithNullValue() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getUriStringWithMaskedPassword()).isEmpty();
    }

    @Test
    public void testPasswordFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPassword()).isEmpty();
    }

    @Test
    public void testUserFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getUserName()).isEmpty();
    }

    @Test
    public void testPortFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPort()).isEqualTo(9999);
    }

    @Test
    public void testHostFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getHostname()).isEmpty();
    }

    @Test
    public void testProtocolFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPassword()).isEmpty();
    }

    @Test
    public void testPathFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getPath()).isEmpty();
    }

    @Test
    public void testUriStringWithMaskedPasswordFromUriWithEmptyString() {
        final ConnectionUri underTest = ConnectionUri.of(null);
        assertThat(underTest.getUriStringWithMaskedPassword()).isEmpty();
    }

}
