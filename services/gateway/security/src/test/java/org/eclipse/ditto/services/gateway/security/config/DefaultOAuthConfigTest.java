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
package org.eclipse.ditto.services.gateway.security.config;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.security.config.DefaultOAuthConfig}.
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
                assumingFields("openIdConnectIssuers").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
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

        softly.assertThat(underTest.getOpenIdConnectIssuers())
                .as(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS.getConfigPath())
                .isEqualTo(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultOAuthConfig underTest = DefaultOAuthConfig.of(oauthConfig);

        softly.assertThat(underTest.getOpenIdConnectIssuers())
                .as(OAuthConfig.OAuthConfigValue.OPENID_CONNECT_ISSUERS.getConfigPath())
                .isEqualTo(Collections.singletonMap(SubjectIssuer.newInstance("google"), "https://accounts.google.com"));
    }

}