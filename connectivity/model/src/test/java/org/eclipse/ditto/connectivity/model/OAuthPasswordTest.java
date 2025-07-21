/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link OAuthPassword}.
 */
public final class OAuthPasswordTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(OAuthPassword.class).verify();
    }

    @Test
    public void testJsonSerialization() {
        final Credentials original = OAuthPassword.newBuilder()
                .clientId("clientId")
                .clientSecret("clientSecret")
                .scope("scope")
                .tokenEndpoint("http://localhost/token")
                .audience("audience")
                .username("username")
                .password("password")
                .build();
        final Credentials deserialized = Credentials.fromJson(original.toJson());
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    public void testJsonSerializationWithoutSecret() {
        final Credentials original = OAuthPassword.newBuilder()
                .clientId("clientId")
                .scope("scope")
                .tokenEndpoint("http://localhost/token")
                .username("username")
                .password("password")
                .build();
        final Credentials deserialized = Credentials.fromJson(original.toJson());
        assertThat(deserialized).isEqualTo(original);
    }
}
