/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link JwtPlaceholder}.
 */
public final class JwtPlaceholderTest {

    private static final JwtPlaceholder UNDER_TEST = new JwtPlaceholder();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JwtPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testResolveRootProperty() {
        final JsonWebToken jwt = createToken("""
                {
                    "sub": "jwt-subject"
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "sub"))
                .containsExactlyInAnyOrder("jwt-subject");
    }

    @Test
    public void testResolveObjectProperty() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "isFull": true
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization"))
                .containsExactlyInAnyOrder("{\"isFull\":true}");
    }

    @Test
    public void testResolveNestedSimpleProperty() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "isFull": true
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/isFull"))
                .containsExactlyInAnyOrder("true");
    }

    @Test
    public void testResolveArrayProperty() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "roles": ["user", "admin"]
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/roles"))
                .containsExactlyInAnyOrder("user", "admin");
    }

    @Test
    public void testResolveSimplePropertyInArray() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "roles": [
                            { "name": "user" },
                            { "name": "admin" }
                        ]
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/roles/name"))
                .containsExactlyInAnyOrder("user", "admin");
    }

    @Test
    public void testResolveObjectPropertyInArray() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "roles": [
                            {
                                "info": { "name": "user" }
                            },
                            {
                                "info": { "name": "admin" }
                            }
                        ]
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/roles/info"))
                .containsExactlyInAnyOrder("{\"name\":\"user\"}", "{\"name\":\"admin\"}");
    }

    @Test
    public void testResolveArrayPropertyInArray() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "roles": [
                            {
                                "name": "user",
                                "claims": [ "read" ]
                            },
                            {
                                "name": "admin",
                                "claims": [ "read", "write" ]
                            }
                        ]
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/roles/claims"))
                .containsExactlyInAnyOrder("read", "write");
    }

    @Test
    public void testResolveNestedPropertyOnSimpleValue() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {
                        "roles": [ "user", "admin" ]
                    }
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/roles/name"))
                .isEmpty();
    }

    @Test
    public void testResolveNestedPropertyOnMissingValue() {
        final JsonWebToken jwt = createToken("""
                {
                    "authorization": {}
                }
                """);
        assertThat(UNDER_TEST.resolveValues(jwt, "authorization/roles"))
                .isEmpty();
    }

    private static JsonWebToken createToken(final String body) {
        final JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        when(jsonWebToken.getBody()).thenReturn(JsonObject.of(body));
        return jsonWebToken;
    }

}
