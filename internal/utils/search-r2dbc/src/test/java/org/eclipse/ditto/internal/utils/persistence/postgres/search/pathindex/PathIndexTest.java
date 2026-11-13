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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Unit tests for {@link PathIndex}: path validation, the deterministic index name and the exact DDL.
 */
public final class PathIndexTest {

    private static final ThingsFieldExpressionFactory FEF = ThingsFieldExpressionFactory.of(Map.of(
            "thingId", "_id",
            "namespace", "_namespace",
            "policyId", "/policyId"));

    @Test
    public void trigramIndexOnAWildcardFeaturePath() {
        final PathIndex index = PathIndex.of("features/*/properties/prop0", PathIndexKind.TRIGRAM, FEF);

        assertThat(index.path()).isEqualTo("features/*/properties/prop0");
        assertThat(index.wpath()).isEqualTo("/features/*/properties/prop0");
        assertThat(index.kind()).isEqualTo(PathIndexKind.TRIGRAM);
        assertThat(index.name()).matches("sf_path_trgm_[0-9a-f]{16}");
        assertThat(index.createStatement()).isEqualTo("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + index.name()
                + " ON search_flat USING gin ((val_text COLLATE \"C.utf8\") gin_trgm_ops) "
                + "WHERE wpath = E'/features/*/properties/prop0' AND val_text IS NOT NULL");
    }

    @Test
    public void trigramExpressionIsTheTableWideOneWithTheSharedCollation() {
        final String statement =
                PathIndex.of("attributes/vendor", PathIndexKind.TRIGRAM, FEF).createStatement();

        assertThat(statement).contains("(val_text COLLATE \"" + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION
                + "\") gin_trgm_ops)");
        // same index expression as the table-wide sf_trgm, so the translator's ilike/like clauses match both
        assertThat(PostgresSearchSchema.ddlStatements())
                .anyMatch(ddl -> ddl.startsWith("CREATE INDEX IF NOT EXISTS sf_trgm ON search_flat "
                        + "USING gin ((val_text COLLATE \"" + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION
                        + "\") gin_trgm_ops) "));
    }

    @Test
    public void nameIsDeterministicAndDistinctPerPathAndKind() {
        final PathIndex first = PathIndex.of("attributes/location/city", PathIndexKind.TRIGRAM, FEF);
        final PathIndex again = PathIndex.of("/attributes/location/city", PathIndexKind.TRIGRAM, FEF);
        final PathIndex other = PathIndex.of("attributes/location/street", PathIndexKind.TRIGRAM, FEF);

        assertThat(again.name()).isEqualTo(first.name());
        assertThat(other.name()).isNotEqualTo(first.name());
        // pinned: a renamed index would be dropped and rebuilt on every deployment of the new code
        assertThat(PathIndex.of("features/*/properties/prop0", PathIndexKind.TRIGRAM, FEF).name())
                .isEqualTo("sf_path_trgm_" + PathIndex.hash16("USING gin ((val_text COLLATE \"C.utf8\") "
                        + "gin_trgm_ops) WHERE wpath = E'/features/*/properties/prop0' AND val_text IS NOT NULL"));
    }

    @Test
    public void nameFitsAPostgresIdentifierForVeryLongPaths() {
        final String longPath = "attributes/" + "a".repeat(500);
        final PathIndex index = PathIndex.of(longPath, PathIndexKind.TRIGRAM, FEF);

        assertThat(index.name().length()).isLessThanOrEqualTo(63);
        assertThat(index.name()).matches("[a-z0-9_]+");
        assertThat(PathIndex.isManagedName(index.name())).isTrue();
    }

    @Test
    public void quotesAndBackslashesAreEscapedInTheLiteral() {
        final PathIndex index = PathIndex.of("attributes/it's/a\\b", PathIndexKind.TRIGRAM, FEF);

        assertThat(index.wpath()).isEqualTo("/attributes/it's/a\\b");
        assertThat(index.createStatement()).contains("WHERE wpath = E'/attributes/it''s/a\\\\b' AND");
    }

    @Test
    public void tildeInKeysUsesTheFlattenerEncoding() {
        assertThat(PathIndex.of("attributes/a~0b", PathIndexKind.TRIGRAM, FEF).wpath()).isEqualTo("/attributes/a~0b");
        assertThat(PathIndex.of("attributes/a~1b", PathIndexKind.TRIGRAM, FEF).wpath())
                .isEqualTo("/attributes/a~01b");
    }

    @Test
    public void slashMappedSimpleFieldIsAllowed() {
        assertThat(PathIndex.of("policyId", PathIndexKind.TRIGRAM, FEF).wpath()).isEqualTo("/policyId");
    }

    @Test
    public void rootMappedFieldIsRejected() {
        assertThatThrownBy(() -> PathIndex.of("thingId", PathIndexKind.TRIGRAM, FEF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thingId")
                .hasMessageContaining("search_flat");
    }

    @Test
    public void unknownFieldIsRejected() {
        assertThatThrownBy(() -> PathIndex.of("foo/bar", PathIndexKind.TRIGRAM, FEF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foo/bar");
    }

    @Test
    public void blankPathIsRejected() {
        assertThatThrownBy(() -> PathIndex.of("  ", PathIndexKind.TRIGRAM, FEF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    public void controlCharactersAreRejected() {
        assertThatThrownBy(() -> PathIndex.of("attributes/a\u0000b", PathIndexKind.TRIGRAM, FEF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control character");
        assertThatThrownBy(() -> PathIndex.of("attributes/a\nb", PathIndexKind.TRIGRAM, FEF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control character");
    }

    @Test
    public void kindIsParsedFromItsConfigValueOnly() {
        assertThat(PathIndexKind.fromConfigValue("trigram")).contains(PathIndexKind.TRIGRAM);
        assertThat(PathIndexKind.fromConfigValue("TRIGRAM")).isEmpty();
        assertThat(PathIndexKind.fromConfigValue("btree")).isEmpty();
    }

    @Test
    public void managedNamesAreRecognisedByPrefixOnly() {
        assertThat(PathIndex.isManagedName("sf_path_trgm_0123456789abcdef")).isTrue();
        assertThat(PathIndex.isManagedName("sf_trgm")).isFalse();
        assertThat(PathIndex.isManagedName("sf_text")).isFalse();
        assertThat(PathIndex.isManagedName("my_sf_path_idx")).isFalse();
    }

    @Test
    public void dropStatementIsConcurrentAndIdempotent() {
        assertThat(PathIndex.dropStatement("sf_path_trgm_0123456789abcdef"))
                .isEqualTo("DROP INDEX CONCURRENTLY IF EXISTS sf_path_trgm_0123456789abcdef");
    }

    @Test
    public void dropStatementRefusesNamesItDoesNotManage() {
        assertThatThrownBy(() -> PathIndex.dropStatement("sf_trgm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathIndex.dropStatement("sf_path_x; DROP TABLE search_flat"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
