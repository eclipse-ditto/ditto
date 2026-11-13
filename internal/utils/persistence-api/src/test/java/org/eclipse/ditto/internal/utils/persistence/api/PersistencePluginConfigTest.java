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

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig.RememberStorePluginIds;
import org.junit.Test;

/**
 * Unit tests for the small value type {@link RememberStorePluginIds} nested in {@link PersistencePluginConfig} and for
 * the shape of the {@link PersistencePluginConfig} accessors (auto-start IDs / remember-store IDs).
 */
public final class PersistencePluginConfigTest {

    @Test
    public void rememberStorePluginIdsExposesJournalAndSnapshotIds() {
        final RememberStorePluginIds ids = RememberStorePluginIds.of(
                "pekko-contrib-mongodb-persistence-connection-remember-journal",
                "pekko-contrib-mongodb-persistence-connection-remember-snapshots");

        assertThat(ids.journalPluginId())
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-journal");
        assertThat(ids.snapshotPluginId())
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-snapshots");
    }

    @Test
    public void rememberStorePluginIdsEqualityIsValueBased() {
        assertThat(RememberStorePluginIds.of("j", "s")).isEqualTo(RememberStorePluginIds.of("j", "s"));
        assertThat(RememberStorePluginIds.of("j", "s")).hasSameHashCodeAs(RememberStorePluginIds.of("j", "s"));
        assertThat(RememberStorePluginIds.of("j", "s")).isNotEqualTo(RememberStorePluginIds.of("j", "x"));
    }

    @Test
    public void rememberStorePluginIdsRejectsNulls() {
        assertThatThrownBy(() -> RememberStorePluginIds.of(null, "s"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RememberStorePluginIds.of("j", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void pluginConfigExposesActiveBackendPluginIdsAndAccessors() {
        final RememberStorePluginIds connectionRemember = RememberStorePluginIds.of(
                "connection-remember-journal", "connection-remember-snapshots");
        final PersistencePluginConfig config = new StubPluginConfig(
                "thing-journal", "thing-snapshots",
                List.of("auto-journal", "auto-snapshots"),
                connectionRemember);

        assertThat(config.getJournalPluginId("thing")).isEqualTo("thing-journal");
        assertThat(config.getSnapshotPluginId("thing")).isEqualTo("thing-snapshots");
        assertThat(config.getAutoStartPluginIds("things")).containsExactly("auto-journal", "auto-snapshots");
        assertThat(config.getRememberStorePluginIds("connection")).isEqualTo(connectionRemember);
    }

    /** Minimal stand-in proving {@link PersistencePluginConfig} is implementable by a backend with plain strings. */
    private record StubPluginConfig(String journalPluginId, String snapshotPluginId,
                                    List<String> autoStartIds, RememberStorePluginIds rememberStoreIds)
            implements PersistencePluginConfig {

        @Override
        public String getJournalPluginId(final String entityType) {
            return journalPluginId;
        }

        @Override
        public String getSnapshotPluginId(final String entityType) {
            return snapshotPluginId;
        }

        @Override
        public List<String> getAutoStartPluginIds(final String serviceName) {
            return autoStartIds;
        }

        @Override
        public RememberStorePluginIds getRememberStorePluginIds(final String entityType) {
            return rememberStoreIds;
        }
    }
}
