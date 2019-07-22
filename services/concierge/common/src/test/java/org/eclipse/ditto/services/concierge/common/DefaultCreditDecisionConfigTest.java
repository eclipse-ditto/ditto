/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.common;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.services.concierge.common.DefaultCreditDecisionConfig}.
 */
public class DefaultCreditDecisionConfigTest {

    private static final Config PERSISTENCE_CLEANUP_CONFIG = ConfigFactory.load("persistence-cleanup-test");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCreditDecisionConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCreditDecisionConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final CreditDecisionConfig underTest = DefaultCreditDecisionConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInterval())
                .as(CreditDecisionConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(CreditDecisionConfig.ConfigValue.INTERVAL.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final CreditDecisionConfig underTest = createFromConfig();

        softly.assertThat(underTest.getInterval())
                .as(CreditDecisionConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(200L));

        softly.assertThat(underTest.getMetricReportTimeout())
                .as(CreditDecisionConfig.ConfigValue.METRIC_REPORT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(300L));

        softly.assertThat(underTest.getTimerThreshold())
                .as(CreditDecisionConfig.ConfigValue.TIMER_THRESHOLD.getConfigPath())
                .isEqualTo(Duration.ofSeconds(400L));

        softly.assertThat(underTest.getCreditPerBatch())
                .as(CreditDecisionConfig.ConfigValue.CREDIT_PER_BATCH.getConfigPath())
                .isEqualTo(500);
    }

    private CreditDecisionConfig createFromConfig() {
        return DefaultPersistenceCleanupConfig.of(PERSISTENCE_CLEANUP_CONFIG).getCreditDecisionConfig();
    }

}
