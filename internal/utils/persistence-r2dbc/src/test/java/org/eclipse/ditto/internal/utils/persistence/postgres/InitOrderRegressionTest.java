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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.persistence.AbstractPersistentActorWithTimers;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Init-order regression test. This is a CI gate: it must fail loudly if the {@code Eventsourced} trait-init order
 * regresses.
 *
 * <h2>What the trap is</h2>
 * Pekko's {@code Eventsourced} trait initialiser calls {@code journalPluginId()} / {@code snapshotPluginId()}
 * <strong>during the super constructor</strong> (the eager {@code maxMessageBatchSize} val,
 * {@code Eventsourced.scala:98-104}), <em>before</em> any subclass field assigned in the subclass constructor body is
 * visible. Therefore the plugin-ID accessors must resolve their value WITHOUT reading a constructor-injected instance
 * field. The production accessors (e.g. {@code ThingPersistenceActor.journalPluginId()}) resolve through the
 * {@link PersistenceBackendProvider} via the field-free {@code context().system()} lookup — {@code ActorCell} pushes the
 * actor context onto the context stack before invoking the constructor ({@code ActorCell.scala:622-623}), so
 * {@code context()} is available even mid-super-call.
 *
 * <h2>What this test asserts, and HOW</h2>
 * It spins up a REAL {@link ActorSystem} configured with the Postgres persistence profile
 * ({@code ditto-postgres-persistence.conf}) and instantiates a probe persistent actor for EACH of the four Ditto
 * persistent-actor entity types — {@code thing}, {@code policy}, {@code connection}, {@code wot-validation-config}
 * (i.e. the entity types of {@code ThingPersistenceActor}, {@code PolicyPersistenceActor},
 * {@code ConnectionPersistenceActor}, {@code WotValidationConfigPersistenceActor}). The probe
 * ({@link PluginIdCapturingProbe}):
 * <ul>
 *     <li>extends Pekko's {@link AbstractPersistentActorWithTimers}, which mixes in the very same {@code Eventsourced}
 *     trait the Ditto persistent actors do, so the super-init call order is reproduced <em>identically</em>;</li>
 *     <li>overrides {@code journalPluginId()} / {@code snapshotPluginId()} to resolve through the
 *     {@link PersistenceBackendProvider} via the field-free {@code context().system()} lookup — byte-for-byte the same
 *     mechanism as {@code ThingPersistenceActor.backendProvider()};</li>
 *     <li><strong>invokes both accessors from its OWN constructor body</strong> and completes a
 *     {@link CompletableFuture} with the captured values. Because the probe's constructor runs only after the
 *     {@code Eventsourced} super-constructor (which already invoked the accessors once), capturing in the constructor
 *     proves the values resolve correctly <em>during construction</em> — strictly stronger than "before the first
 *     message".</li>
 * </ul>
 * If the accessors had been wired to a constructor-injected field instead, the {@code Eventsourced} super-init call
 * would have read {@code null} and the actor would have crashed before the probe constructor ran — the future would
 * never complete and this test would time out (fail loudly).
 *
 * <h2>Why this does NOT need a live database</h2>
 * Plugin-ID resolution is a pure HOCON read on the provider; it happens before any persistence I/O. The probe never
 * sends a command, never recovers, and never persists, so Pekko never instantiates the journal/snapshot plugin classes
 * and no connection pool is built. The {@code ditto.postgresql.ssl.mode = disable} keeps the client boot guard quiet
 * even if anything did touch the config.
 *
 * <h2>Why Props-injection was NOT adopted</h2>
 * The tempting "harden it by injecting the plugin IDs through {@code Props}" is a FALSE fix and is deliberately NOT used
 * here. {@code Props.create(clazz, args...)} uses {@code ArgsReflectConstructor} — a reflective constructor call — so an
 * injected plugin-ID value lands in a subclass field that is <em>still {@code null}</em> when {@code Eventsourced}'s
 * eager {@code maxMessageBatchSize} val calls {@code journalPluginId()} during super-init: the EXACT same null-field
 * trap. (Only the discouraged function/closure {@code Props} form captures, and that form is unsafe for sharded/remote
 * actors because it is not serializable.) This test therefore exercises ONLY the field-free {@code context().system()}
 * lookup, the primary and correct mechanism. {@link #propsInjectionWouldHitTheNullFieldTrap()} demonstrates the trap
 * empirically so the rationale cannot silently rot.
 */
public final class InitOrderRegressionTest {

    private static ActorSystem system;

    /** The four Ditto persistent-actor entity types and their expected Postgres plugin IDs (per the HOCON profile). */
    private static final Map<String, String[]> EXPECTED_PLUGIN_IDS = Map.of(
            // entityType -> { expected journal plugin id, expected snapshot plugin id }
            "thing", new String[]{"ditto-postgres-things-journal", "ditto-postgres-things-snapshots"},
            "policy", new String[]{"ditto-postgres-policies-journal", "ditto-postgres-policies-snapshots"},
            "connection", new String[]{"ditto-postgres-connections-journal", "ditto-postgres-connections-snapshots"},
            "wot-validation-config", new String[]{"ditto-postgres-wot-journal", "ditto-postgres-wot-snapshots"}
    );

    @BeforeClass
    public static void setUp() {
        // A real ActorSystem configured with the Postgres persistence profile. ditto-postgres-persistence.conf selects the
        // PostgresPersistenceBackendProvider, declares the plugin-id mappings + alternate auto-start lists, AND supplies
        // the ditto.postgresql.* client defaults (SSL disabled below so no boot guard / no connection is ever attempted
        // offline).
        //
        // The journal blocks now bind per-entity JSONB event adapters (C-1: ThingPostgresEventAdapter et al.). Pekko's
        // Eventsourced super-init eagerly instantiates every configured event adapter via Persistence.createAdapters
        // (Eventsourced.scala:103), BEFORE the probe constructor body runs. Those adapter classes live in the SERVICE
        // modules (things/policies/connectivity service), which this internal/utils/persistence-r2dbc test module does
        // not — and must not — depend on, so they are absent from this test classpath and instantiation would throw
        // ClassNotFoundException and crash the probe before it can capture the plugin IDs. This test asserts ONLY
        // plugin-ID resolution (a pure HOCON read that happens before any adapter/plugin I/O), so we blank the
        // event-adapter wiring of every Postgres journal block here. The real services keep the adapters on their
        // classpath; the adapter round-trip itself is covered by the per-entity *PostgresEventAdapterTest in the
        // service modules. `null` clears the key (HOCON: a null value removes it and a lower-priority object fallback
        // does not resurge it).
        //
        // Same reasoning applies to the profile's pekko.persistence.*.auto-start-* lists (Task 6): they now name real
        // Pekko plugin IDs, so Persistence's OWN extension constructor — triggered eagerly by Eventsourced.$init$,
        // i.e. DURING the probe's super-init, before the probe's own constructor body runs — walks the auto-start list
        // and eagerly calls journalFor() for every entry. Each plugin's `plugin-dispatcher` (e.g.
        // "thing-journal-persistence-dispatcher") is defined only in the owning SERVICE's <svc>.conf (things.conf,
        // policies.conf, connectivity.conf), none of which this test loads, so the eager journalFor() throws a
        // ConfigurationException that propagates out of Eventsourced.$init$ and crashes the probe before it ever
        // reaches its own constructor body — the sink future then times out. Blank the auto-start lists here so
        // Persistence's constructor has nothing to eagerly start; the real services set/narrow these lists with their
        // own dispatchers available, and the auto-start contract itself is proved by PostgresProfileHoconLintTest,
        // not this init-order test.
        final String blankAutoStart = String.join("\n",
                "pekko.persistence.journal.auto-start-journals = []",
                "pekko.persistence.snapshot-store.auto-start-snapshot-stores = []",
                "");
        final String blankAdapters = String.join("\n",
                "ditto-postgres-things-journal.event-adapters = null",
                "ditto-postgres-things-journal.event-adapter-bindings = null",
                "ditto-postgres-policies-journal.event-adapters = null",
                "ditto-postgres-policies-journal.event-adapter-bindings = null",
                "ditto-postgres-connections-journal.event-adapters = null",
                "ditto-postgres-connections-journal.event-adapter-bindings = null",
                "ditto-postgres-wot-journal.event-adapters = null",
                "ditto-postgres-wot-journal.event-adapter-bindings = null",
                "");
        final Config config = ConfigFactory.parseString(
                        "ditto.postgresql.ssl.mode = \"disable\"\n" + blankAdapters + blankAutoStart)
                .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                .withFallback(ConfigFactory.load())
                .resolve();
        system = ActorSystem.create("InitOrderRegressionTest", config);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void thingPluginIdsResolveToPostgresDuringConstruction() {
        final var sink = new CompletableFuture<CapturedPluginIds>();
        assertResolvedDuringConstruction("thing", ThingProbe.props(sink), sink);
    }

    @Test
    public void policyPluginIdsResolveToPostgresDuringConstruction() {
        final var sink = new CompletableFuture<CapturedPluginIds>();
        assertResolvedDuringConstruction("policy", PolicyProbe.props(sink), sink);
    }

    @Test
    public void connectionPluginIdsResolveToPostgresDuringConstruction() {
        final var sink = new CompletableFuture<CapturedPluginIds>();
        assertResolvedDuringConstruction("connection", ConnectionProbe.props(sink), sink);
    }

    @Test
    public void wotValidationConfigPluginIdsResolveToPostgresDuringConstruction() {
        final var sink = new CompletableFuture<CapturedPluginIds>();
        assertResolvedDuringConstruction("wot-validation-config", WotValidationConfigProbe.props(sink), sink);
    }

    /**
     * Instantiates a probe persistent actor for one of the four Ditto entity types, then asserts that the plugin IDs
     * captured INSIDE the probe constructor (after the {@code Eventsourced} super-init already invoked the accessors)
     * equal the configured Postgres plugin IDs. A regressed init order would crash the actor before the probe
     * constructor completed, so the {@code sink} future would never complete and this would time out.
     */
    private void assertResolvedDuringConstruction(final String entityType, final Props probeProps,
            final CompletableFuture<CapturedPluginIds> sink) {
        final String[] expected = EXPECTED_PLUGIN_IDS.get(entityType);

        // Instantiating the actor triggers Eventsourced super-init -> journalPluginId()/snapshotPluginId(), then the
        // probe constructor captures the (already-resolved) values and completes the sink future.
        system.actorOf(probeProps, "probe-" + entityType);

        final CapturedPluginIds captured = sink.orTimeout(10, TimeUnit.SECONDS).join();

        assertThat(captured.journalPluginId)
                .as("journalPluginId() resolved during construction for entity <%s>", entityType)
                .isEqualTo(expected[0]);
        assertThat(captured.snapshotPluginId)
                .as("snapshotPluginId() resolved during construction for entity <%s>", entityType)
                .isEqualTo(expected[1]);
    }

    /**
     * Empirically demonstrates the null-field trap that makes {@code Props}-injection a NON-fix: a
     * persistent actor whose plugin-ID accessor reads a constructor-injected field reads {@code null} during the
     * {@code Eventsourced} super-init and crashes — never reaching its own constructor body. We assert it never reports
     * success (its post-super-init capture never fires) within a bounded wait, which is exactly why this codebase keeps
     * the field-free {@code context().system()} lookup instead.
     */
    @Test
    public void propsInjectionWouldHitTheNullFieldTrap() {
        final CompletableFuture<String> reachedConstructorBody = new CompletableFuture<>();
        final TestKit watcher = new TestKit(system);

        final ActorRef trap = system.actorOf(
                FieldInjectedTrapProbe.props("ditto-postgres-things-journal", reachedConstructorBody),
                "field-injected-trap");
        watcher.watch(trap);

        // The Eventsourced super-init reads the still-null injected field and the actor crashes during construction
        // (default supervision stops it). It must NEVER report that it reached its own constructor body with a non-null
        // value -> confirms Props/field injection does not escape the trap.
        watcher.expectTerminated(Duration.ofSeconds(10), trap);
        assertThat(reachedConstructorBody.isDone())
                .as("field-injected probe must NOT complete its post-super-init capture: super-init crashed on null")
                .isFalse();
    }

    /** Immutable holder for the plugin IDs captured during the probe's construction. */
    private static final class CapturedPluginIds {
        private final String journalPluginId;
        private final String snapshotPluginId;

        private CapturedPluginIds(final String journalPluginId, final String snapshotPluginId) {
            this.journalPluginId = journalPluginId;
            this.snapshotPluginId = snapshotPluginId;
        }
    }

    /**
     * Base probe that mirrors {@code ThingPersistenceActor}'s plugin-ID resolution exactly: it resolves the IDs through
     * the {@link PersistenceBackendProvider} via the field-free {@code context().system()} lookup, and captures both
     * values from its OWN constructor body (proving resolution succeeded DURING construction, after the
     * {@code Eventsourced} super-init already invoked the accessors once).
     * <p>
     * CRITICAL: the entity type is supplied by the abstract {@link #entityType()} method — overridden in
     * each subclass to return a compile-time string <em>literal</em>, exactly like the four real actors hardcode their
     * own type (e.g. {@code ThingConstants.ENTITY_TYPE.toString()}). It is NOT a constructor-injected field. A field
     * would be {@code null} when {@code Eventsourced} super-init calls {@code journalPluginId()} → the same null-field
     * trap that defeats Props-injection. The {@code captured} sink IS a field, but it is read only in the constructor
     * body (after super-init), never by the accessors, so it is safe.
     */
    abstract static class AbstractPluginIdProbe extends AbstractPersistentActorWithTimers {

        private final transient CompletableFuture<CapturedPluginIds> captured;

        @SuppressWarnings("this-escape")
        AbstractPluginIdProbe(final CompletableFuture<CapturedPluginIds> captured) {
            this.captured = captured;
            // Capture DURING construction. journalPluginId()/snapshotPluginId() resolve via the provider using ONLY
            // context().system() + the field-free entityType() literal — no constructor-injected field is read.
            captured.complete(new CapturedPluginIds(journalPluginId(), snapshotPluginId()));
        }

        /** @return the entity type, as a field-free compile-time literal (mirrors each real actor's hardcoded type). */
        abstract String entityType();

        private PersistenceBackendProvider backendProvider() {
            final var actorSystem = context().system();
            return PersistenceBackendProvider.get(actorSystem,
                    ScopedConfig.dittoExtension(actorSystem.settings().config()));
        }

        @Override
        public String journalPluginId() {
            return backendProvider().getJournalPluginId(entityType());
        }

        @Override
        public String snapshotPluginId() {
            return backendProvider().getSnapshotPluginId(entityType());
        }

        @Override
        public String persistenceId() {
            return entityType() + ":init-order-probe";
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().build();
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder().build();
        }

        // NOTE: after the constructor completes (and the sink future captures the resolved IDs), Pekko proceeds to
        // materialise the journal ActorRef and recover. The Postgres journal/snapshot plugins are not yet wired for
        // Pekko's reflective (Config)/no-arg instantiation, so this triggers an
        // ActorInitializationException ("no matching constructor ... for arguments []") that is LOGGED but happens
        // strictly AFTER the assertion point. It is expected and harmless here: it actually demonstrates that plugin-ID
        // resolution precedes any plugin instantiation. The test asserts only the construction-time capture.
    }

    /** Probe for {@code ThingPersistenceActor}'s entity type. */
    public static final class ThingProbe extends AbstractPluginIdProbe {
        ThingProbe(final CompletableFuture<CapturedPluginIds> captured) {
            super(captured);
        }

        static Props props(final CompletableFuture<CapturedPluginIds> captured) {
            return Props.create(ThingProbe.class, captured);
        }

        @Override
        String entityType() {
            return "thing";
        }
    }

    /** Probe for {@code PolicyPersistenceActor}'s entity type. */
    public static final class PolicyProbe extends AbstractPluginIdProbe {
        PolicyProbe(final CompletableFuture<CapturedPluginIds> captured) {
            super(captured);
        }

        static Props props(final CompletableFuture<CapturedPluginIds> captured) {
            return Props.create(PolicyProbe.class, captured);
        }

        @Override
        String entityType() {
            return "policy";
        }
    }

    /** Probe for {@code ConnectionPersistenceActor}'s entity type. */
    public static final class ConnectionProbe extends AbstractPluginIdProbe {
        ConnectionProbe(final CompletableFuture<CapturedPluginIds> captured) {
            super(captured);
        }

        static Props props(final CompletableFuture<CapturedPluginIds> captured) {
            return Props.create(ConnectionProbe.class, captured);
        }

        @Override
        String entityType() {
            return "connection";
        }
    }

    /** Probe for {@code WotValidationConfigPersistenceActor}'s entity type. */
    public static final class WotValidationConfigProbe extends AbstractPluginIdProbe {
        WotValidationConfigProbe(final CompletableFuture<CapturedPluginIds> captured) {
            super(captured);
        }

        static Props props(final CompletableFuture<CapturedPluginIds> captured) {
            return Props.create(WotValidationConfigProbe.class, captured);
        }

        @Override
        String entityType() {
            return "wot-validation-config";
        }
    }

    /**
     * Counter-probe that demonstrates the null-field trap: its plugin-ID accessor reads a CONSTRUCTOR-INJECTED field
     * (the "Props-injection" anti-pattern). Pekko's {@code Eventsourced} super-init calls {@code journalPluginId()}
     * before this field is assigned, so it reads {@code null} and the plugin lookup fails during construction — the
     * actor crashes before its own constructor body runs.
     */
    public static final class FieldInjectedTrapProbe extends AbstractPersistentActorWithTimers {

        // Assigned in the constructor BODY -> still null when Eventsourced super-init reads it. THIS is the trap.
        private final String injectedJournalPluginId;
        private final transient CompletableFuture<String> reachedConstructorBody;

        @SuppressWarnings("this-escape")
        private FieldInjectedTrapProbe(final String injectedJournalPluginId,
                final CompletableFuture<String> reachedConstructorBody) {
            this.injectedJournalPluginId = injectedJournalPluginId;
            this.reachedConstructorBody = reachedConstructorBody;
            // If we ever get here with the field set, the trap did not fire (it does — super-init crashed first).
            reachedConstructorBody.complete(injectedJournalPluginId);
        }

        static Props props(final String injectedJournalPluginId,
                final CompletableFuture<String> reachedConstructorBody) {
            return Props.create(FieldInjectedTrapProbe.class, injectedJournalPluginId, reachedConstructorBody);
        }

        @Override
        public String journalPluginId() {
            // Reads the injected field, which is null during Eventsourced super-init -> NPE-equivalent failure.
            return injectedJournalPluginId.trim();
        }

        @Override
        public String persistenceId() {
            return "trap:init-order-probe";
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().build();
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder().build();
        }
    }
}
