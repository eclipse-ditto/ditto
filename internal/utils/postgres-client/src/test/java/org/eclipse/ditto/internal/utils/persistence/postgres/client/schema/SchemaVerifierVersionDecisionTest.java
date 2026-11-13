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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.Test;

/**
 * Unit tests for the full rolling-upgrade decision matrix in {@link SchemaVerifier}: how the stored
 * {@code schema_version} row (version, checksum, {@code min_compatible_version}) decides whether this code runs its
 * DDL ({@link SchemaVersionDecision#APPLY}), runs no DDL and writes no row
 * ({@link SchemaVersionDecision#COMPATIBLE_NEWER}), or refuses. Covers both the boot path
 * ({@link SchemaVerifier#verifyStoredVersion}) and the self-heal path
 * ({@link SchemaVerifier#verifyStoredVersionForHeal}), and the same-version fast path
 * ({@link SchemaVerifier#decideComponentDdl}). Pure logic, no live PostgreSQL. The single home of the version
 * decision's unit tests; {@code persistence-r2dbc}'s {@code SchemaVerifierTest} covers the live-catalog guard.
 */
public final class SchemaVerifierVersionDecisionTest {

    private static final String COMPONENT = "ditto-postgres-persistence";
    private static final String CODE_CHECKSUM = "code-checksum";

    // ---------------------------------------------------------------------------------------------------------------
    // boot path
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void bootWithoutStoredRowApplies() {
        // first boot: create, verify, insert the row.
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, null, null, null))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void bootWithOlderStoredVersionApplies() {
        // upgrade in flight: idempotent DDL, row advanced (also writes this code's min_compatible_version).
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 3, "old-checksum", 2, 2))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void bootWithOlderLegacyStoredVersionApplies() {
        // A pre-T05 row (min_compatible_version NULL) that is older than the code is a plain upgrade.
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 3, "old-checksum", 2, null))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void bootWithSameVersionAndSameChecksumApplies() {
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, CODE_CHECKSUM, 2, 2))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void bootWithSameVersionLegacyRowApplies() {
        // A pre-T05 row at the same version: NULL min is treated as the version (2 <= 2), checksum matches -> APPLY,
        // and the caller's upsert then back-fills this code's min_compatible_version.
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, CODE_CHECKSUM, 2, null))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void bootWithSameVersionAndDifferentChecksumRefuses() {
        assertThatThrownBy(() -> SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, "drifted", 2, 2))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("checksum mismatch")
                .hasMessageContaining("drift")
                .hasMessageContaining(COMPONENT);
    }

    @Test
    public void bootWithNewerStoredVersionAndLowerMinCompatibleVersionIsCompatibleNewer() {
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, "newer-checksum", 4, 1))
                .isEqualTo(SchemaVersionDecision.COMPATIBLE_NEWER);
    }

    @Test
    public void bootWithNewerStoredVersionAndMinCompatibleVersionEqualToCodeIsCompatibleNewer() {
        // boundary: min_compatible_version == code version is still compatible (<=).
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, "newer-checksum", 3, 2))
                .isEqualTo(SchemaVersionDecision.COMPATIBLE_NEWER);
    }

    @Test
    public void bootWithNewerStoredVersionIgnoresTheChecksumOfTheNewerVersion() {
        // A newer version's checksum is by definition different from this code's; it is not compared.
        assertThat(SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, CODE_CHECKSUM + "-v3", 3, 2))
                .isEqualTo(SchemaVersionDecision.COMPATIBLE_NEWER);
    }

    @Test
    public void bootWithNewerStoredVersionAndHigherMinCompatibleVersionRefuses() {
        assertThatThrownBy(() -> SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, "newer-checksum", 3,
                3))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("Refusing to boot")
                .hasMessageContaining("downgrade")
                .hasMessageContaining("min_compatible_version 3")
                .hasMessageContaining("stored schema version 3")
                .hasMessageContaining("version 2")
                .hasMessageContaining(COMPONENT);
    }

    @Test
    public void bootWithNewerLegacyStoredVersionTreatsNullMinCompatibleVersionAsTheStoredVersion() {
        // A pre-T05 row (column missing -> added as NULL): NULL means min_compatible_version = version = 3 > 2
        // -> refuse, exactly the pre-T05 downgrade guard.
        assertThatThrownBy(() -> SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, "newer-checksum", 3,
                null))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("min_compatible_version 3")
                .hasMessageContaining("stored schema version 3");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // self-heal path: only the "newer stored version" rule applies (no checksum guard, unchanged from before)
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void healWithoutStoredRowApplies() {
        // a wiped database (row gone too): heal recreates the tables, as before.
        assertThat(SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, null, null))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void healWithOlderOrSameStoredVersionApplies() {
        assertThat(SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 3, 2, 2))
                .isEqualTo(SchemaVersionDecision.APPLY);
        assertThat(SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, 2, 2))
                .isEqualTo(SchemaVersionDecision.APPLY);
        assertThat(SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, 2, null))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void healWithNewerCompatibleStoredVersionRunsNoDdl() {
        assertThat(SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, 3, 2))
                .isEqualTo(SchemaVersionDecision.COMPATIBLE_NEWER);
    }

    @Test
    public void healWithNewerIncompatibleStoredVersionFails() {
        assertThatThrownBy(() -> SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, 3, 3))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("self-heal")
                .hasMessageContaining("min_compatible_version 3")
                .hasMessageContaining(COMPONENT);
    }

    @Test
    public void healWithNewerLegacyStoredVersionFails() {
        assertThatThrownBy(() -> SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, 3, null))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("min_compatible_version 3");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // same-version fast path (decideComponentDdl)
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void sameVersionAndChecksumWithEveryRelationPresentIsUpToDate() {
        final SchemaVersionDecision version =
                SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, CODE_CHECKSUM, 2, 2);
        final boolean current = SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, 2, CODE_CHECKSUM);

        assertThat(SchemaVerifier.decideComponentDdl(version, current, Set.of()))
                .isEqualTo(SchemaVersionDecision.UP_TO_DATE);
    }

    @Test
    public void sameVersionWithAMissingIndexRunsTheDdl() {
        assertThat(SchemaVerifier.decideComponentDdl(SchemaVersionDecision.APPLY, true, Set.of("sf_num")))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void sameVersionWithUnnameableDdlRunsTheDdl() {
        assertThat(SchemaVerifier.decideComponentDdl(SchemaVersionDecision.APPLY, true, null))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void firstBootAndUpgradeRunTheDdlEvenIfEveryRelationExists() {
        assertThat(SchemaVerifier.decideComponentDdl(SchemaVersionDecision.APPLY,
                SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, null, null), Set.of()))
                .isEqualTo(SchemaVersionDecision.APPLY);
        assertThat(SchemaVerifier.decideComponentDdl(SchemaVersionDecision.APPLY,
                SchemaVerifier.storedRowIsCurrent(3, CODE_CHECKSUM, 2, "old-checksum"), Set.of()))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void newerCompatibleStoredVersionKeepsTheRollingUpgradePath() {
        final SchemaVersionDecision version =
                SchemaVerifier.verifyStoredVersion(COMPONENT, CODE_CHECKSUM, 2, "v3-checksum", 3, 2);

        assertThat(SchemaVerifier.decideComponentDdl(version, false, Set.of()))
                .isEqualTo(SchemaVersionDecision.COMPATIBLE_NEWER);
        assertThat(SchemaVerifier.decideComponentDdl(version, true, Set.of()))
                .isEqualTo(SchemaVersionDecision.COMPATIBLE_NEWER);
    }

    @Test
    public void healWithSameVersionButDriftedChecksumRunsTheDdl() {
        // The heal never refuses on drift (verifyStoredVersionForHeal), and a drifted row is never "current".
        final SchemaVersionDecision version = SchemaVerifier.verifyStoredVersionForHeal(COMPONENT, 2, 2, 2);

        assertThat(SchemaVerifier.decideComponentDdl(version,
                SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, 2, "drifted"), Set.of()))
                .isEqualTo(SchemaVersionDecision.APPLY);
    }

    @Test
    public void storedRowIsCurrentOnlyForTheSameVersionAndChecksum() {
        assertThat(SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, 2, CODE_CHECKSUM)).isTrue();
        assertThat(SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, 1, CODE_CHECKSUM)).isFalse();
        assertThat(SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, 3, CODE_CHECKSUM)).isFalse();
        assertThat(SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, 2, "other")).isFalse();
        assertThat(SchemaVerifier.storedRowIsCurrent(2, CODE_CHECKSUM, null, null)).isFalse();
    }

}
