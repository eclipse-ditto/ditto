/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultEventConfig}.
 */
public final class DefaultEventConfigTest {

    private static Config snapshotTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        snapshotTestConf = ConfigFactory.load("event-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultEventConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultEventConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultEventConfig underTest = DefaultEventConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getHistoricalHeadersToPersist())
                .as(EventConfig.EventConfigValue.HISTORICAL_HEADERS_TO_PERSIST.getConfigPath())
                .isEqualTo(EventConfig.EventConfigValue.HISTORICAL_HEADERS_TO_PERSIST.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultEventConfig underTest = DefaultEventConfig.of(snapshotTestConf);

        softly.assertThat(underTest.getHistoricalHeadersToPersist())
                .as(EventConfig.EventConfigValue.HISTORICAL_HEADERS_TO_PERSIST.getConfigPath())
                .isEqualTo(List.of(DittoHeaderDefinition.ORIGINATOR.getKey(), "foo"));
    }
}
