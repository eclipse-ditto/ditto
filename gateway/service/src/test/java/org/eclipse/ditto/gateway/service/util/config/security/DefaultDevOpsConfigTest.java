/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;


public final class DefaultDevOpsConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private static Config devopsTestConf;

    @BeforeClass
    public static void initTestFixture() {
        devopsTestConf = ConfigFactory.load("devops-test");
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultDevOpsConfig.class,
                areImmutable(),
                provided(DefaultOAuthConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultDevOpsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultDevOpsConfig underTest = DefaultDevOpsConfig.of(ConfigFactory.empty());

        assertDefaultValueFor(underTest.isSecured(), DevOpsConfig.DevOpsConfigValue.SECURED);
        assertDefaultValueFor(underTest.getDevopsAuthenticationMethod().getMethodName(),
                DevOpsConfig.DevOpsConfigValue.DEVOPS_AUTHENTICATION_METHOD);
        assertDefaultValueFor(underTest.getPassword(), DevOpsConfig.DevOpsConfigValue.PASSWORD);
        assertDefaultValueFor(underTest.getDevopsOAuth2Subjects(), DevOpsConfig.DevOpsConfigValue.DEVOPS_OAUTH2_SUBJECTS);
        assertDefaultValueFor(underTest.isStatusSecured(), DevOpsConfig.DevOpsConfigValue.STATUS_SECURED);
        assertDefaultValueFor(underTest.getStatusAuthenticationMethod().getMethodName(),
                DevOpsConfig.DevOpsConfigValue.STATUS_AUTHENTICATION_METHOD);
        assertDefaultValueFor(underTest.getStatusPassword(), DevOpsConfig.DevOpsConfigValue.STATUS_PASSWORD);
        assertDefaultValueFor(underTest.getStatusOAuth2Subjects(), DevOpsConfig.DevOpsConfigValue.STATUS_OAUTH2_SUBJECTS);
    }

    void assertDefaultValueFor(final Object value, final DevOpsConfig.DevOpsConfigValue devOpsConfigValue) {
        softly.assertThat(value)
                .as(devOpsConfigValue.getConfigPath())
                .isEqualTo(devOpsConfigValue.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultDevOpsConfig underTest = DefaultDevOpsConfig.of(devopsTestConf);

        assertConfiguredValueFor(underTest.isSecured(), DevOpsConfig.DevOpsConfigValue.SECURED, false);
        assertConfiguredValueFor(underTest.getDevopsAuthenticationMethod().getMethodName(),
                DevOpsConfig.DevOpsConfigValue.DEVOPS_AUTHENTICATION_METHOD, "basic");
        assertConfiguredValueFor(underTest.getPassword(), DevOpsConfig.DevOpsConfigValue.PASSWORD, "bumlux");
        assertConfiguredValueFor(underTest.getDevopsOAuth2Subjects(),
                DevOpsConfig.DevOpsConfigValue.DEVOPS_OAUTH2_SUBJECTS, List.of("someissuer:a", "someissuer:b"));
        assertConfiguredValueFor(underTest.isStatusSecured(), DevOpsConfig.DevOpsConfigValue.SECURED, false);
        assertConfiguredValueFor(underTest.getStatusAuthenticationMethod().getMethodName(),
                DevOpsConfig.DevOpsConfigValue.STATUS_AUTHENTICATION_METHOD, "oauth2");
        assertConfiguredValueFor(underTest.getStatusPassword(), DevOpsConfig.DevOpsConfigValue.STATUS_PASSWORD,
                "1234");
        assertConfiguredValueFor(underTest.getStatusOAuth2Subjects(),
                DevOpsConfig.DevOpsConfigValue.STATUS_OAUTH2_SUBJECTS, List.of("someissuer:c"));
    }

    <T> void assertConfiguredValueFor(final T actualValue, final DevOpsConfig.DevOpsConfigValue devOpsConfigValue,
            final T expectedValue) {
        softly.assertThat(actualValue)
                .as(devOpsConfigValue.getConfigPath())
                .isEqualTo(expectedValue);
    }

}
