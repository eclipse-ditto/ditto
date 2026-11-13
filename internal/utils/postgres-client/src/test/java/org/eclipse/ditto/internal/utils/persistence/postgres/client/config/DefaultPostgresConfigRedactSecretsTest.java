/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link DefaultPostgresConfig#redactSecrets(String)} (ticket r2mlh-m12, security ticket
 * ditto-postgres-smv).
 *
 * <p>R2DBC URIs use the compound scheme {@code r2dbc:postgresql:} (and {@code r2dbc:pool:postgresql:} for the pool
 * wrapper), which {@link java.net.URI} parses as scheme {@code r2dbc} plus an opaque remainder — so userinfo is NOT
 * surfaced by {@code URI.getUserInfo()}. Redaction is therefore performed by targeted string surgery on the raw
 * {@code uri} String: rewrite the userinfo password to {@code ****} and replace the values of known secret query
 * parameters ({@code sslPassword}, {@code password}, {@code sslcert}, {@code sslkey}) with {@code ****}. Any failure
 * falls back to the literal {@code <redacted>} without throwing.</p>
 */
@RunWith(Enclosed.class)
public final class DefaultPostgresConfigRedactSecretsTest {

    /**
     * Exact-output cases: the redacted String must equal the expected value verbatim.
     */
    @RunWith(Parameterized.class)
    public static final class ExactOutputTest {

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> cases() {
            return Arrays.asList(new Object[][]{
                    {
                            "userinfo + sslPassword present",
                            "r2dbc:postgresql://user:secret@host:5432/db?sslPassword=x",
                            "r2dbc:postgresql://user:****@host:5432/db?sslPassword=****"
                    },
                    {
                            "no-port, no-query",
                            "r2dbc:postgresql://u:p@h/db",
                            "r2dbc:postgresql://u:****@h/db"
                    },
                    {
                            "pool wrapper prefix + alternate password param",
                            "r2dbc:pool:postgresql://u:p@h/db?maxIdleTime=PT1M&password=p2",
                            "r2dbc:pool:postgresql://u:****@h/db?maxIdleTime=PT1M&password=****"
                    },
                    {
                            "malformed URI falls back to <redacted>",
                            "r2dbc:postgresql://user:secr]et@[host",
                            "<redacted>"
                    },
                    {
                            "no userinfo and no secret params passes through verbatim",
                            "r2dbc:postgresql://host:5432/db?applicationName=ditto",
                            "r2dbc:postgresql://host:5432/db?applicationName=ditto"
                    },
                    {
                            // ditto-postgres-smv: a literal '@' inside the password (two '@' in the authority) must
                            // never leave a plaintext fragment — bail to the <redacted> fallback.
                            "literal @ in password falls back to <redacted>",
                            "r2dbc:postgresql://user:p@ss@host:5432/db",
                            "<redacted>"
                    },
                    {
                            // ditto-postgres-smv: null input is the documented fallback path.
                            "null input falls back to <redacted>",
                            null,
                            "<redacted>"
                    },
            });
        }

        @Parameterized.Parameter
        public String description;

        @Parameterized.Parameter(1)
        public String input;

        @Parameterized.Parameter(2)
        public String expected;

        @Test
        public void redactsAsExpected() {
            assertThat(DefaultPostgresConfig.redactSecrets(input))
                    .as(description)
                    .isEqualTo(expected);
        }

        @Test
        public void neverThrows() {
            assertThatCode(() -> DefaultPostgresConfig.redactSecrets(input))
                    .as(description + " — must not throw")
                    .doesNotThrowAnyException();
        }
    }

    /**
     * Security cases (ticket ditto-postgres-smv): the redacted output must not contain plaintext secret material,
     * regardless of the exact masking strategy.
     */
    public static final class NoLeakTest {

        @Test
        public void literalAtSignInPasswordDoesNotLeakFragment() {
            // The first-'@' bug would have produced "user:****@ss@host:5432/db", leaking the "@ss" fragment.
            final String redacted =
                    DefaultPostgresConfig.redactSecrets("r2dbc:postgresql://user:p@ss@host:5432/db");
            assertThat(redacted)
                    .as("literal @ in password must not leak the @ss fragment")
                    .doesNotContain("@ss");
            assertThat(redacted)
                    .as("literal @ in password must not leak the p@ss plaintext")
                    .doesNotContain("p@ss");
        }

        @Test
        public void upperCaseSchemeStillRedactsUserinfo() {
            // The case-sensitive scheme bug would have passed this through verbatim, leaving "secret" visible.
            final String redacted =
                    DefaultPostgresConfig.redactSecrets("R2DBC:POSTGRESQL://user:secret@host/db");
            assertThat(redacted)
                    .as("upper-case scheme must still have its userinfo password redacted")
                    .doesNotContain("secret");
        }
    }
}
