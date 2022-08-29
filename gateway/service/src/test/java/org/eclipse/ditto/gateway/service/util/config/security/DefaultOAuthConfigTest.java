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
package org.eclipse.ditto.gateway.service.util.config.security;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultOAuthConfig}.
 */
public final class DefaultOAuthConfigTest {

    private static Config oauthConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        oauthConfig = ConfigFactory.load("oauth-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultOAuthConfig.class, areImmutable(),
                provided(SubjectIssuer.class).isAlsoImmutable(),
                assumingFields("openIdConnectIssuers").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                assumingFields(
                        "openIdConnectIssuersExtension").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultOAuthConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultOAuthConfig underTest = DefaultOAuthConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getProtocol()).isEqualTo("https");

        softly.assertThat(underTest.getAllowedClockSkew()).isEqualTo(Duration.ofSeconds(10));

        softly.assertThat(underTest.getOpenIdConnectIssuers())
                .as(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS.getConfigPath())
                .isEqualTo(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS.getDefaultValue());

        softly.assertThat(underTest.getOpenIdConnectIssuers())
                .as(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS_EXTENSION.getConfigPath())
                .isEqualTo(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS_EXTENSION.getDefaultValue());

        softly.assertThat(underTest.getTokenIntegrationSubject())
                .isEqualTo(OAuthConfig.OAuthConfigValue.TOKEN_INTEGRATION_SUBJECT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultOAuthConfig underTest = DefaultOAuthConfig.of(oauthConfig);

        softly.assertThat(underTest.getProtocol()).isEqualTo("https");

        softly.assertThat(underTest.getAllowedClockSkew()).isEqualTo(Duration.ofSeconds(20));

        softly.assertThat(underTest.getOpenIdConnectIssuers())
                .as(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS.getConfigPath())
                .isEqualTo(
                        Map.of(
                                SubjectIssuer.newInstance("google"),
                                DefaultSubjectIssuerConfig.of(
                                        List.of("https://accounts.google.com"),
                                        List.of(
                                                "{{ jwt:sub }}",
                                                "{{ jwt:sub }}/{{ jwt:scope }}",
                                                "{{ jwt:sub }}/{{ jwt:scope }}@{{ jwt:client_id }}",
                                                "{{ jwt:sub }}/{{ jwt:scope }}@{{ jwt:non_existing }}",
                                                "{{ jwt:roles/support }}"
                                        )
                                ),
                                SubjectIssuer.newInstance("some-other"),
                                DefaultSubjectIssuerConfig.of(
                                        List.of(
                                                "https://one.com",
                                                "two.com",
                                                "https://three.com"
                                        ),
                                        List.of(
                                                "{{ jwt:sub }}"
                                        )
                                )
                        ));

        softly.assertThat(underTest.getOpenIdConnectIssuersExtension())
                .as(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS_EXTENSION.getConfigPath())
                .isEqualTo(Collections.singletonMap(
                        SubjectIssuer.newInstance("additional"),
                        DefaultSubjectIssuerConfig.of(
                                List.of("https://additional.google.com"),
                                List.of("{{ jwt:sub }}")
                        )
                ));

        softly.assertThat(underTest.getTokenIntegrationSubject()).isEqualTo("ditto:ditto");
    }
}
