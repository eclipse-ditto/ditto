/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.api.config;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.persistence.api.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.api.config.ReadPreference;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultSearchPersistenceConfig}.
 */
public final class DefaultSearchPersistenceConfigTest {


    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("updater-persistence-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSearchPersistenceConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.readConcern())
                .as(SearchPersistenceConfig.ConfigValue.READ_CONCERN.getConfigPath())
                .isEqualTo(ReadConcern.ofReadConcern(
                        (String) SearchPersistenceConfig.ConfigValue.READ_CONCERN.getDefaultValue())
                        .orElseThrow());

        softly.assertThat(underTest.readPreference())
                .as(SearchPersistenceConfig.ConfigValue.READ_PREFERENCE.getConfigPath())
                .isEqualTo(ReadPreference.ofReadPreference(
                        (String) SearchPersistenceConfig.ConfigValue.READ_PREFERENCE.getDefaultValue())
                        .orElseThrow());

        softly.assertThat(underTest.getPolicyModificationCausedSearchIndexUpdateThrottling())
                .as("Throttling Config should have default values")
                .isEqualTo(ThrottlingConfig.of(ConfigFactory.empty()));
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.of(config);

        softly.assertThat(underTest.readConcern())
                .as(SearchPersistenceConfig.ConfigValue.READ_CONCERN.getConfigPath())
                .isEqualTo(ReadConcern.AVAILABLE);

        softly.assertThat(underTest.readPreference())
                .as(SearchPersistenceConfig.ConfigValue.READ_PREFERENCE.getConfigPath())
                .isEqualTo(ReadPreference.SECONDARY_PREFERRED);

        softly.assertThat(underTest.getPolicyModificationCausedSearchIndexUpdateThrottling())
                .as("Throttling Config should match the configuration file")
                .isNotNull();
    }

    @Test
    public void ofScopedConfigReadsSettingsWithoutTheNestedPersistencePath() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.ofScopedConfig(
                ConfigFactory.parseString("readPreference = \"nearest\""));

        softly.assertThat(underTest.readPreference())
                .as(SearchPersistenceConfig.ConfigValue.READ_PREFERENCE.getConfigPath())
                .isEqualTo(ReadPreference.NEAREST);

        softly.assertThat(underTest.readConcern())
                .as("read concern falls back to its default")
                .isEqualTo(ReadConcern.DEFAULT);
    }

    @Test
    public void ofBlockInheritingTakesMissingReadConcernFromInherited() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.ofBlockInheriting(
                ConfigFactory.parseString("readPreference = secondary"),
                inherited(ReadPreference.NEAREST, ReadConcern.LINEARIZABLE));

        softly.assertThat(underTest.readPreference()).as("named by the block").isEqualTo(ReadPreference.SECONDARY);
        softly.assertThat(underTest.readConcern()).as("inherited").isEqualTo(ReadConcern.LINEARIZABLE);
    }

    @Test
    public void ofBlockInheritingTakesMissingReadPreferenceFromInherited() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.ofBlockInheriting(
                ConfigFactory.parseString("readConcern = majority"),
                inherited(ReadPreference.NEAREST, ReadConcern.LINEARIZABLE));

        softly.assertThat(underTest.readPreference()).as("inherited").isEqualTo(ReadPreference.NEAREST);
        softly.assertThat(underTest.readConcern()).as("named by the block").isEqualTo(ReadConcern.MAJORITY);
    }

    @Test
    public void ofBlockInheritingBlockValuesWin() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.ofBlockInheriting(
                ConfigFactory.parseString("readPreference = secondaryPreferred\nreadConcern = available"),
                inherited(ReadPreference.NEAREST, ReadConcern.LINEARIZABLE));

        softly.assertThat(underTest.readPreference()).isEqualTo(ReadPreference.SECONDARY_PREFERRED);
        softly.assertThat(underTest.readConcern()).isEqualTo(ReadConcern.AVAILABLE);
    }

    @Test
    public void ofBlockInheritingEmptyBlockEqualsInheritedReadSettings() {
        final SearchPersistenceConfig underTest = DefaultSearchPersistenceConfig.ofBlockInheriting(
                ConfigFactory.empty(), inherited(ReadPreference.NEAREST, ReadConcern.LINEARIZABLE));

        softly.assertThat(underTest.readPreference()).isEqualTo(ReadPreference.NEAREST);
        softly.assertThat(underTest.readConcern()).isEqualTo(ReadConcern.LINEARIZABLE);
        softly.assertThat(underTest.getPolicyModificationCausedSearchIndexUpdateThrottling())
                .as("throttling is not inherited")
                .isEqualTo(ThrottlingConfig.of(ConfigFactory.empty()));
    }

    private static SearchPersistenceConfig inherited(final ReadPreference readPreference,
            final ReadConcern readConcern) {
        return DefaultSearchPersistenceConfig.ofScopedConfig(ConfigFactory.parseString(
                "readPreference = " + readPreference.getName() + "\nreadConcern = " + readConcern.getName() +
                        "\nthrottling.limit = 7"));
    }

}
