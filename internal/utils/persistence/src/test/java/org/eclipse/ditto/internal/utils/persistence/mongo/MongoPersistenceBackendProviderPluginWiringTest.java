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

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.function.Consumer;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Pins that the MongoDB provider runs no plugin-wiring check at boot: a MongoDB deployment that wires a journal plugin
 * class of its own (a wrapper or a fork of the MongoDB plugin), or an auto-start entry without a plugin block, keeps
 * booting exactly as on a Ditto without pluggable persistence. The check exists only on the PostgreSQL provider.
 * <p>
 * Each case gets its own actor system carrying only its condition, so each test fails on its own if a check were ever
 * introduced on the MongoDB provider. Pekko Persistence is not loaded by {@code ActorSystem.create}, so the block-less
 * auto-start entry does not stop the actor system from starting.
 */
public final class MongoPersistenceBackendProviderPluginWiringTest {

    private static final String CUSTOM_JOURNAL_CLASS = "com.example.persistence.CustomMongoJournal";
    private static final String JOURNAL_WITHOUT_BLOCK = "acme-journal-without-block";

    @Test
    public void customJournalPluginClassDoesNotFailTheBoot() {
        withProvider("pekko-contrib-mongodb-persistence-things-journal.class = \"" + CUSTOM_JOURNAL_CLASS + "\"",
                provider -> assertThatCode(() -> provider.verifyPluginWiring("things"))
                        .as("the MongoDB provider inherits the no-op: a custom plugin class must boot as before")
                        .doesNotThrowAnyException());
    }

    @Test
    public void autoStartEntryWithoutPluginBlockDoesNotFailTheBoot() {
        withProvider("pekko.persistence.journal.auto-start-journals = [ \"" + JOURNAL_WITHOUT_BLOCK + "\" ]",
                provider -> assertThatCode(() -> provider.verifyPluginWiring("things"))
                        .as("the MongoDB provider inherits the no-op: an entry without a block must boot as before")
                        .doesNotThrowAnyException());
    }

    private static void withProvider(final String overrides,
            final Consumer<MongoPersistenceBackendProvider> assertion) {
        final Config config = ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load("reference"));
        final ActorSystem system = ActorSystem.create("MongoPersistenceBackendProviderPluginWiringTest", config);
        try {
            assertion.accept(new MongoPersistenceBackendProvider(system,
                    ScopedConfig.dittoExtension(config).getConfig("persistence-backend-provider.extension-config")));
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }
}
