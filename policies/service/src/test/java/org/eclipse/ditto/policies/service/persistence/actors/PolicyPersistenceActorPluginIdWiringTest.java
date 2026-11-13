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
package org.eclipse.ditto.policies.service.persistence.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Directly exercises the C-3 actor seam for {@link PolicyPersistenceActor}: that the actor resolves its Pekko
 * journal/snapshot plugin IDs by <em>delegating</em> to the active {@link PersistenceBackendProvider} keyed by the
 * policy entity type — rather than returning the hardcoded {@code pekko-contrib-mongodb-persistence-policies-*}
 * constants.
 *
 * <p><b>Why a stub provider.</b> The Postgres provider lives in a module the policies service does not depend on, so we
 * cannot select it here. Instead we register a tiny in-test {@link StubBackendProvider} via the extension config; it
 * returns clearly-distinguishable sentinel plugin IDs for the {@code "policy"} entity type (and would throw for any
 * other entity type). We then boot the REAL {@link PolicyPersistenceActor} and read back the plugin IDs the actor's own
 * {@code journalPluginId()}/{@code snapshotPluginId()} overrides return.</p>
 *
 * <p><b>What this catches.</b></p>
 * <ul>
 *     <li>A revert to {@code return JOURNAL_PLUGIN_ID} (the Mongo constant): the actor would return
 *     {@code pekko-contrib-mongodb-persistence-policies-journal}, not the sentinel &rarr; assertion fails.</li>
 *     <li>Passing the wrong entity-type string to the provider: the stub throws {@code IllegalArgumentException} for any
 *     type other than {@code "policy"}, so the actor fails to start (the plugin-id methods run during Pekko's
 *     {@code Eventsourced} trait init) &rarr; test fails.</li>
 * </ul>
 *
 * <p>The sentinel IDs are aliased to the in-memory journal/snapshot plugins so the actor starts without a database.</p>
 */
public final class PolicyPersistenceActorPluginIdWiringTest {

    private static final String SENTINEL_JOURNAL_ID = "policy-backend-provider-journal";
    private static final String SENTINEL_SNAPSHOT_ID = "policy-backend-provider-snapshots";

    /**
     * The config:
     * <ul>
     *     <li>selects {@link StubBackendProvider} as the persistence backend provider,</li>
     *     <li>aliases the sentinel plugin IDs to the in-memory journal/snapshot (so the actor boots with no DB),</li>
     *     <li>falls back to the policies {@code test.conf} for everything else (policy config, dispatchers, ...).</li>
     * </ul>
     */
    private static final Config CONFIG = ConfigFactory.parseString(
                    "ditto.extensions.persistence-backend-provider.extension-class = \""
                            + StubBackendProvider.class.getName() + "\"\n"
                            + SENTINEL_JOURNAL_ID + " {\n"
                            + "  class = \"io.github.alstanchev.pekko.persistence.inmemory.journal.InMemoryAsyncWriteJournal\"\n"
                            + "  plugin-dispatcher = \"policy-persistence-dispatcher\"\n"
                            + "  ask-timeout = 10s\n"
                            + "}\n"
                            + SENTINEL_SNAPSHOT_ID + " {\n"
                            + "  class = \"io.github.alstanchev.pekko.persistence.inmemory.snapshot.InMemorySnapshotStore\"\n"
                            + "  plugin-dispatcher = \"policy-persistence-dispatcher\"\n"
                            + "  ask-timeout = 10s\n"
                            + "}\n")
            .withFallback(ConfigFactory.load("test"));

    private ActorSystem actorSystem;

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void actorResolvesPluginIdsThroughBackendProviderAndNotTheMongoConstants() {
        actorSystem = ActorSystem.create("PekkoTestSystem", CONFIG);
        final TestProbe pubSubMediator = new TestProbe(actorSystem, "mock-pubSub-mediator");
        final DittoReadJournal readJournal = mock(DittoReadJournal.class);

        // Booting the real actor runs Pekko's Eventsourced trait init, which calls journalPluginId()/snapshotPluginId();
        // if the actor passed a wrong entity type the StubBackendProvider would throw and creation would fail here.
        final TestActorRef<PolicyPersistenceActor> ref = TestActorRef.create(actorSystem,
                PolicyPersistenceActor.propsForTests(PolicyId.of("test.ns", "policy-wiring"), readJournal,
                        pubSubMediator.ref(), actorSystem.deadLetters(), actorSystem),
                "policyPersistenceActor");

        final PolicyPersistenceActor actor = ref.underlyingActor();

        assertThat(actor.journalPluginId())
                .as("PolicyPersistenceActor must delegate journalPluginId() to the backend provider (C-3)")
                .isEqualTo(SENTINEL_JOURNAL_ID)
                .isNotEqualTo("pekko-contrib-mongodb-persistence-policies-journal");
        assertThat(actor.snapshotPluginId())
                .as("PolicyPersistenceActor must delegate snapshotPluginId() to the backend provider (C-3)")
                .isEqualTo(SENTINEL_SNAPSHOT_ID)
                .isNotEqualTo("pekko-contrib-mongodb-persistence-policies-snapshots");
    }

    /**
     * Minimal test provider: returns sentinel IDs for the {@code "policy"} entity type and rejects everything else, so
     * the test fails loudly if {@link PolicyPersistenceActor} ever keys the provider under the wrong entity type.
     * Loaded reflectively by the Ditto extension mechanism via its {@code (ActorSystem, Config)} constructor.
     */
    public static final class StubBackendProvider implements PersistenceBackendProvider {

        @SuppressWarnings("unused")
        public StubBackendProvider(final ActorSystem actorSystem, final Config extensionConfig) {
            // no-op: this stub is config-free.
        }

        @Override
        public String getJournalPluginId(final String entityType) {
            return requirePolicy(entityType, SENTINEL_JOURNAL_ID);
        }

        @Override
        public String getSnapshotPluginId(final String entityType) {
            return requirePolicy(entityType, SENTINEL_SNAPSHOT_ID);
        }

        @Override
        public DittoReadJournal getReadJournal() {
            throw new UnsupportedOperationException("not needed for the plugin-id wiring test");
        }

        @Override
        public SnapshotCodec snapshotCodec() {
            // Minimal pass-through codec: the test never snapshots, but the AbstractPersistenceActor constructor
            // resolves the codec eagerly (C3). Encoding/decoding are identity over JsonObject.
            return new SnapshotCodec() {
                @Override
                public Object encode(final JsonObject json) {
                    return json;
                }

                @Override
                public JsonObject decode(final Object rawSnapshot) {
                    return (JsonObject) rawSnapshot;
                }
            };
        }

        private static String requirePolicy(final String entityType, final String sentinel) {
            if (!"policy".equals(entityType)) {
                throw new IllegalArgumentException("PolicyPersistenceActor must key the backend provider under the "
                        + "<policy> entity type, but it requested <" + entityType + ">");
            }
            return sentinel;
        }
    }
}
