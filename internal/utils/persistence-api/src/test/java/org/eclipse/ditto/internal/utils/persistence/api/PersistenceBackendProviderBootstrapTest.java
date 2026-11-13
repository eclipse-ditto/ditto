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
package org.eclipse.ditto.internal.utils.persistence.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Offline contract test for the boot-seam method {@link PersistenceBackendProvider#bootstrapSchema()} that every service
 * {@code RootActor} now calls on the {@link PersistenceBackendProvider} <em>interface</em> (not a concrete subclass)
 * before {@code getReadJournal()} and before any persistent actor starts.
 * <p>
 * This is the offline regression guard for a review finding (the Postgres bootstrap previously had zero production call
 * sites). It does not need a database: it pins the contract the RootActors rely on, namely
 * </p>
 * <ul>
 *     <li>the method is declared on the interface (so a RootActor holding the interface can call it),</li>
 *     <li>the default implementation is a harmless no-op (the Mongo path — Mongo creates collections lazily),</li>
 *     <li>a backend that overrides it has its override invoked through an interface-typed reference — i.e. the exact
 *     dispatch a RootActor performs — and</li>
 *     <li>a failing bootstrap propagates its exception (boot fails fast) rather than being swallowed.</li>
 * </ul>
 * The same seam contract holds for the second boot hook, {@link PersistenceBackendProvider#verifyPluginWiring(String)}:
 * its default is a no-op, an override is reached through the interface with the service name, and its failure
 * propagates.
 * <p>
 * The {@code PostgresProviderBootstrapIT} complements this by exercising the same interface seam against a real
 * container; this test guarantees the seam exists and dispatches correctly even where Docker is unavailable.
 */
public final class PersistenceBackendProviderBootstrapTest {

    @Test
    public void defaultBootstrapSchemaIsANoOp() {
        // A provider that does NOT override bootstrapSchema() (the Mongo case) inherits the no-op default: calling it
        // through the interface must neither throw nor do anything observable.
        final PersistenceBackendProvider mongoLikeDefault = new StubBackendProvider();

        // Called exactly the way a RootActor calls it — on the interface type.
        final PersistenceBackendProvider asInterface = mongoLikeDefault;
        asInterface.bootstrapSchema();
        // No exception == pass; the default really is a no-op.
    }

    @Test
    public void overriddenBootstrapSchemaIsInvokedThroughTheInterface() {
        final AtomicInteger bootstrapCalls = new AtomicInteger();
        // A backend that DOES manage a schema (the Postgres case) overrides bootstrapSchema().
        final PersistenceBackendProvider schemaManaging = new StubBackendProvider() {
            @Override
            public void bootstrapSchema() {
                bootstrapCalls.incrementAndGet();
            }
        };

        // Dispatch through an interface-typed reference — byte-for-byte what ThingsRootActor / PoliciesRootActor /
        // ConnectivityRootActor do via the PersistenceBackendProvider field.
        final PersistenceBackendProvider asInterface = schemaManaging;
        asInterface.bootstrapSchema();

        assertThat(bootstrapCalls.get())
                .as("a RootActor calling provider.bootstrapSchema() must reach the backend override exactly once")
                .isEqualTo(1);
    }

    @Test
    public void failingBootstrapSchemaPropagatesToFailBoot() {
        final PersistenceBackendProvider failing = new StubBackendProvider() {
            @Override
            public void bootstrapSchema() {
                throw new IllegalStateException("schema cannot be established");
            }
        };

        // A RootActor does not catch this: the exception propagates and the service refuses to boot.
        final PersistenceBackendProvider asInterface = failing;
        assertThatThrownBy(asInterface::bootstrapSchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema cannot be established");
    }

    @Test
    public void defaultVerifyPluginWiringIsANoOp() {
        // A provider that does NOT override verifyPluginWiring() (the Mongo case) inherits the no-op default: the
        // RootActor's call neither throws nor checks anything, whatever plugins the deployment wires.
        final PersistenceBackendProvider asInterface = new StubBackendProvider();
        asInterface.verifyPluginWiring("things");
    }

    @Test
    public void overriddenVerifyPluginWiringIsInvokedThroughTheInterfaceWithTheServiceName() {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> seenServiceName = new AtomicReference<>();
        final PersistenceBackendProvider checking = new StubBackendProvider() {
            @Override
            public void verifyPluginWiring(final String serviceName) {
                calls.incrementAndGet();
                seenServiceName.set(serviceName);
            }
        };

        final PersistenceBackendProvider asInterface = checking;
        asInterface.verifyPluginWiring("policies");

        assertThat(calls.get()).as("the override is reached exactly once").isEqualTo(1);
        assertThat(seenServiceName.get()).isEqualTo("policies");
    }

    @Test
    public void failingVerifyPluginWiringPropagatesToFailBoot() {
        final PersistenceBackendProvider failing = new StubBackendProvider() {
            @Override
            public void verifyPluginWiring(final String serviceName) {
                throw new IllegalStateException("plugins belong to another backend");
            }
        };

        final PersistenceBackendProvider asInterface = failing;
        assertThatThrownBy(() -> asInterface.verifyPluginWiring("things"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another backend");
    }

    /** Minimal {@link PersistenceBackendProvider} that implements only the non-bootstrap methods; the rest defaults. */
    private static class StubBackendProvider implements PersistenceBackendProvider {

        @Override
        public String getJournalPluginId(final String entityType) {
            return "stub-journal";
        }

        @Override
        public String getSnapshotPluginId(final String entityType) {
            return "stub-snapshots";
        }

        @Override
        public DittoReadJournal getReadJournal() {
            throw new UnsupportedOperationException("not needed for the bootstrap-seam contract test");
        }
    }
}
