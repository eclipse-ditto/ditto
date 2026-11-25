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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchDatabasePrerequisites.Facts;
import org.junit.Test;

/**
 * Unit tests of the decision part of {@link PostgresSearchDatabasePrerequisites} (no database); the catalog query and
 * the refusal through the provider are covered by {@code PostgresSearchCtypeMatrixIT}.
 */
public final class PostgresSearchDatabasePrerequisitesTest {

    private static final String C_CTYPE_REFUSAL = "PostgreSQL search schema bootstrap failed: database 'ditto' "
            + "(encoding UTF8, LC_CTYPE C) cannot host the search schema: LC_CTYPE 'C' is not UTF-8 based. The sf_trgm "
            + "trigram index behind like()/ilike() needs the libc collation \"C.utf8\", which exists only in "
            + "UTF8-encoded databases on a server whose OS provides the C.UTF-8 locale. pg_trgm also lower-cases and "
            + "splits words with the database's LC_CTYPE, so on a C/POSIX or other non-UTF-8 LC_CTYPE ilike() silently "
            + "misses matches (e.g. U+0130 or U+212A in a value against i or k in the pattern) whenever the planner "
            + "uses that index. Create the search database with UTF8 encoding and a UTF-8 ctype, e.g. CREATE DATABASE "
            + "ditto TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8'; on CloudNativePG, set "
            + "bootstrap.initdb.localeCType: C.UTF-8 (the default encoding is UTF8).";

    @Test
    public void ctypesWithAUtf8CodesetQualify() {
        assertThat(List.of("C.UTF-8", "C.utf8", "C.UTF8", "en_US.utf8", "en_US.UTF-8", "de_DE.UTF-8@euro"))
                .allSatisfy(ctype -> assertThat(PostgresSearchDatabasePrerequisites.isUtf8Ctype(ctype))
                        .as(ctype)
                        .isTrue());
    }

    @Test
    public void ctypesWithoutAUtf8CodesetDoNotQualify() {
        assertThat(List.of("C", "POSIX", "", "en_US", "en_US.ISO-8859-1", "de_DE@euro", "C.UTF-16",
                "English_United States.1252"))
                .allSatisfy(ctype -> assertThat(PostgresSearchDatabasePrerequisites.isUtf8Ctype(ctype))
                        .as(ctype)
                        .isFalse());
    }

    @Test
    public void aUtf8DatabaseWithAUtf8CtypeAndTheCollationIsAccepted() {
        assertThat(PostgresSearchDatabasePrerequisites.refusal(new Facts("ditto", "UTF8", "en_US.utf8", true)))
                .isEmpty();
        assertThat(PostgresSearchDatabasePrerequisites.refusal(new Facts("ditto", "UTF8", "C.UTF-8", true)))
                .isEmpty();
    }

    @Test
    public void aCCtypeIsRefusedWithTheExactMessage() {
        assertThat(PostgresSearchDatabasePrerequisites.refusal(new Facts("ditto", "UTF8", "C", true)))
                .contains(C_CTYPE_REFUSAL);
    }

    @Test
    public void everyUnmetPrerequisiteIsNamedInOrder() {
        assertThat(PostgresSearchDatabasePrerequisites.unmetPrerequisites(
                new Facts("sqlascii", "SQL_ASCII", "C", false))).containsExactly(
                "the encoding is SQL_ASCII, not UTF8",
                "the collation \"C.utf8\" does not exist for encoding SQL_ASCII",
                "LC_CTYPE 'C' is not UTF-8 based");
        assertThat(PostgresSearchDatabasePrerequisites.unmetPrerequisites(
                new Facts("nocollation", "UTF8", "C.UTF-8", false)))
                .containsExactly("the collation \"C.utf8\" does not exist for encoding UTF8");
        assertThat(PostgresSearchDatabasePrerequisites.unmetPrerequisites(new Facts("posix", "UTF8", "POSIX", true)))
                .containsExactly("LC_CTYPE 'POSIX' is not UTF-8 based");
    }

    @Test
    public void theRemedyNamesTheDatabase() {
        assertThat(PostgresSearchDatabasePrerequisites.refusal(new Facts("search", "LATIN1", "C", false)))
                .hasValueSatisfying(message -> assertThat(message)
                        .startsWith("PostgreSQL search schema bootstrap failed: database 'search' (encoding LATIN1, "
                                + "LC_CTYPE C) cannot host the search schema: the encoding is LATIN1, not UTF8; ")
                        .contains("CREATE DATABASE search TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' "
                                + "LC_CTYPE 'C.UTF-8';")
                        .endsWith("bootstrap.initdb.localeCType: C.UTF-8 (the default encoding is UTF8)."));
    }

    @Test
    public void theQueryLooksUpTheCollationOfTheTrigramIndexForTheDatabaseEncoding() {
        assertThat(PostgresSearchDatabasePrerequisites.DATABASE_SQL)
                .contains("c.collname = '" + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION + "'")
                .contains("c.collencoding IN (-1, d.encoding)")
                .contains("d.datctype")
                .contains("d.datname = current_database()");
    }

}
