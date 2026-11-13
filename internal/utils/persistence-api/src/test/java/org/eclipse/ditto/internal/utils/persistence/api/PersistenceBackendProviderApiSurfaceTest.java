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

import org.junit.Test;

/**
 * Contract test for the additive, backend-neutral API surface of the pluggable-persistence provider: the new
 * {@link PersistenceBackendProvider} collaborator accessors ({@code pluginConfig()}, {@code operations(String)},
 * {@code healthCheck()}, {@code streaming(String)}, {@code snapshotCodec()}, {@code payloadStore(String)}).
 * <p>
 * Every new accessor has a {@code default} body that throws {@link UnsupportedOperationException}. This is the
 * "backends compile incrementally" contract from the plan: a current {@link PersistenceBackendProvider} implementation
 * (e.g. the Mongo provider) does not need to implement any of the new methods to compile, and a caller that touches an
 * accessor a backend has not yet wired gets a loud, explicit failure rather than a silent wrong answer. The Mongo
 * (phase B) and Postgres (phase E) providers override these one by one.
 */
public final class PersistenceBackendProviderApiSurfaceTest {

    private final PersistenceBackendProvider provider = new MinimalProvider();

    @Test
    public void pluginConfigDefaultThrowsUnsupported() {
        assertThatThrownBy(provider::pluginConfig)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void operationsDefaultThrowsUnsupported() {
        assertThatThrownBy(() -> provider.operations("thing"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void healthCheckDefaultThrowsUnsupported() {
        assertThatThrownBy(provider::healthCheck)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void streamingDefaultThrowsUnsupported() {
        assertThatThrownBy(() -> provider.streaming("thing", pid -> null, entityId -> null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void snapshotCodecDefaultThrowsUnsupported() {
        assertThatThrownBy(provider::snapshotCodec)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void payloadStoreDefaultThrowsUnsupported() {
        assertThatThrownBy(() -> provider.payloadStore("connection"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void preExistingMethodsRemainAbstractAndUnaffected() {
        // The minimal provider only implements the original abstract methods; that it compiles and answers proves the
        // new accessors are purely additive defaults and did not change the existing contract.
        assertThat(provider.getJournalPluginId("thing")).isEqualTo("stub-journal");
        assertThat(provider.getSnapshotPluginId("thing")).isEqualTo("stub-snapshots");
    }

    /**
     * A provider that implements ONLY the abstract methods. If any collaborator accessor were abstract (not a default),
     * this class would fail to compile — so it is itself part of the "compile incrementally" assertion.
     */
    private static final class MinimalProvider implements PersistenceBackendProvider {

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
            throw new UnsupportedOperationException("not needed for the API-surface contract test");
        }
    }
}
