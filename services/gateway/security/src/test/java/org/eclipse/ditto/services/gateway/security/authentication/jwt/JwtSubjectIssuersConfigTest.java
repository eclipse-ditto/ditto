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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtSubjectIssuersConfig}.
 */
public final class JwtSubjectIssuersConfigTest {

    private static final JwtSubjectIssuerConfig JWT_SUBJECT_ISSUER_CONFIG_GOOGLE;
    private static final Set<JwtSubjectIssuerConfig> JWT_SUBJECT_ISSUER_CONFIGS;
    private static final JwtSubjectIssuersConfig JWT_SUBJECT_ISSUERS_CONFIG;

    static {
        JWT_SUBJECT_ISSUER_CONFIG_GOOGLE = new JwtSubjectIssuerConfig("accounts.google.com", SubjectIssuer.GOOGLE);
        JWT_SUBJECT_ISSUER_CONFIGS = new HashSet<>();
        JWT_SUBJECT_ISSUER_CONFIGS.add(JWT_SUBJECT_ISSUER_CONFIG_GOOGLE);
        JWT_SUBJECT_ISSUERS_CONFIG = new JwtSubjectIssuersConfig(JWT_SUBJECT_ISSUER_CONFIGS);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JwtSubjectIssuersConfig.class, areImmutable(),
                assumingFields("subjectIssuerConfigMap")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JwtSubjectIssuersConfig.class).verify();
    }

    @Test
    public void issuerWithProtocolWorks() {
        final Optional<JwtSubjectIssuerConfig> configItem =
                JWT_SUBJECT_ISSUERS_CONFIG.getConfigItem("https://accounts.google.com");
        assertThat(configItem).hasValue(JWT_SUBJECT_ISSUER_CONFIG_GOOGLE);
    }

    @Test
    public void issuerWithoutProtocolWorks() {
        final Optional<JwtSubjectIssuerConfig> configItem =
                JWT_SUBJECT_ISSUERS_CONFIG.getConfigItem("accounts.google.com");
        assertThat(configItem).hasValue(JWT_SUBJECT_ISSUER_CONFIG_GOOGLE);
    }

}
