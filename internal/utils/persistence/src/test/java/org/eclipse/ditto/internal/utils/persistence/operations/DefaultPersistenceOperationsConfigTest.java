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
package org.eclipse.ditto.internal.utils.persistence.operations;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultPersistenceOperationsConfig}.
 */
public final class DefaultPersistenceOperationsConfigTest {

    private static Config persistenceOperationsTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        persistenceOperationsTestConfig = ConfigFactory.load("persistence-operations-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPersistenceOperationsConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPersistenceOperationsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultPersistenceOperationsConfig underTest =
                DefaultPersistenceOperationsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getDelayAfterPersistenceActorShutdown())
                .as(PersistenceOperationsConfig.PersistenceOperationsConfigValue.DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN.getConfigPath())
                .isEqualTo(PersistenceOperationsConfig.PersistenceOperationsConfigValue.DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultPersistenceOperationsConfig underTest =
                DefaultPersistenceOperationsConfig.of(persistenceOperationsTestConfig);

        softly.assertThat(underTest.getDelayAfterPersistenceActorShutdown())
                .as(PersistenceOperationsConfig.PersistenceOperationsConfigValue.DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN.getConfigPath())
                .isEqualTo(Duration.ofSeconds(13L));
    }

}
