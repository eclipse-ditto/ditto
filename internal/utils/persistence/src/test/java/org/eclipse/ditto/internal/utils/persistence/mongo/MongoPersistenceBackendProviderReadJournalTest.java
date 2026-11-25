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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests the per-entity-type read journals of {@link MongoPersistenceBackendProvider}. The configuration mirrors the
 * things service: the things journal is the single auto-started journal, the WoT validation config journal has its
 * own journal, read and snapshot blocks, and the policies blocks are absent. No MongoDB is contacted: the driver
 * connects lazily and building a read journal sends no command.
 */
public final class MongoPersistenceBackendProviderReadJournalTest {

    private static final String THINGS_JOURNAL = "pekko-contrib-mongodb-persistence-things-journal";
    private static final String THINGS_SNAPSHOTS = "pekko-contrib-mongodb-persistence-things-snapshots";
    private static final String WOT_JOURNAL = "pekko-contrib-mongodb-persistence-wot-validation-config-journal";
    private static final String WOT_SNAPSHOTS = "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots";

    private static final Config REFERENCE_EXTENSION_CONFIG =
            ScopedConfig.dittoExtension(ConfigFactory.load("reference"))
                    .getConfig("persistence-backend-provider.extension-config");

    private ActorSystem system;
    private MongoPersistenceBackendProvider provider;

    @Before
    public void setUp() {
        final Config config = ConfigFactory.parseString(
                "ditto.mongodb.uri = \"mongodb://localhost:27017/test\"\n"
                        + "pekko.persistence.journal.auto-start-journals = [\"" + THINGS_JOURNAL + "\"]\n"
                        + "pekko.persistence.snapshot-store.auto-start-snapshot-stores = [\"" + THINGS_SNAPSHOTS
                        + "\"]\n"
                        + journalBlocks(THINGS_JOURNAL, "things_journal")
                        + snapshotBlock(THINGS_SNAPSHOTS, "things_snaps")
                        + journalBlocks(WOT_JOURNAL, "wot_validation_config_journal")
                        + snapshotBlock(WOT_SNAPSHOTS, "wot_validation_config_snaps"))
                .withFallback(ConfigFactory.load("reference"));
        system = ActorSystem.create("MongoPersistenceBackendProviderReadJournalTest", config);
        provider = new MongoPersistenceBackendProvider(system, REFERENCE_EXTENSION_CONFIG);
    }

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void readJournalOfTheServiceEntityIsTheSharedInstance() {
        assertThat(provider.getReadJournal("thing")).isSameAs(provider.getReadJournal());
    }

    @Test
    public void readJournalOfAnotherEntityIsADifferentInstance() {
        final DittoReadJournal wotReadJournal = provider.getReadJournal("wot-validation-config");

        assertThat(wotReadJournal)
                .isInstanceOf(MongoReadJournal.class)
                .isNotSameAs(provider.getReadJournal());
        assertThat(provider.getReadJournal("wot-validation-config")).isSameAs(wotReadJournal);
    }

    @Test
    public void readJournalOfAnEntityWithoutJournalConfigurationFails() {
        assertThat(provider.getReadJournal("wot-validation-config")).isInstanceOf(MongoReadJournal.class);

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> provider.getReadJournal("policy"))
                .withMessageContaining("policy")
                .withMessageContaining("pekko-contrib-mongodb-persistence-policies-journal");
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> provider.getReadJournal("policy"))
                .withMessageContaining("policy");
    }

    private static String journalBlocks(final String journalPluginId, final String collection) {
        return journalPluginId + " {\n"
                + "  class = \"pekko.contrib.persistence.mongodb.MongoJournal\"\n"
                + "  overrides.journal-collection = \"" + collection + "\"\n"
                + "}\n"
                + journalPluginId + "-read {\n"
                + "  class = \"pekko.contrib.persistence.mongodb.MongoReadJournal\"\n"
                + "  overrides.journal-collection = \"" + collection + "\"\n"
                + "}\n";
    }

    private static String snapshotBlock(final String snapshotPluginId, final String collection) {
        return snapshotPluginId + " {\n"
                + "  class = \"pekko.contrib.persistence.mongodb.MongoSnapshots\"\n"
                + "  overrides.snaps-collection = \"" + collection + "\"\n"
                + "}\n";
    }

}
