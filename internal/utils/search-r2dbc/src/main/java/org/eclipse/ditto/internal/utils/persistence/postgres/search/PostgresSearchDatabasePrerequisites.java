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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The database prerequisites of the PostgreSQL search schema that its DDL cannot check, verified at the start of every
 * schema-bootstrap attempt, before the bootstrap touches the database
 * ({@link PostgresSearchPersistenceProvider#bootstrapSchema()}):
 * <ul>
 *     <li>the database encoding is {@code UTF8};</li>
 *     <li>the libc collation {@value PostgresSearchSchema#TRIGRAM_INDEX_COLLATION} exists for that encoding: the
 *     {@code sf_trgm} trigram index and the {@code like}/{@code ilike} translation use it, and it is imported only into
 *     UTF8 databases, from the server OS's {@code C.UTF-8} locale;</li>
 *     <li>the database {@code LC_CTYPE} is UTF-8 based (e.g. {@code C.UTF-8}, {@code en_US.utf8}).</li>
 * </ul>
 * <p>
 * The last one is about correct results. {@code pg_trgm} lower-cases and finds word characters with the database's
 * {@code LC_CTYPE}, never with the index collation, while {@code ILIKE … COLLATE "C.utf8"} rechecks with Unicode
 * lower-casing. On a {@code C}/{@code POSIX} ctype every non-ASCII character separates words for {@code pg_trgm}, so a
 * value containing a character whose lower case is an ASCII letter (U+0130 → {@code i}, the Kelvin sign U+212A →
 * {@code k}) never yields the trigrams an {@code ilike} pattern asks for: whenever the planner uses {@code sf_trgm}, the
 * thing is silently missing from the result. The ctype is judged by its name (its codeset must be UTF-8), also for a
 * database whose default collation provider is ICU: {@code pg_trgm} uses the libc ctype either way.
 * </p>
 * <p>
 * A database that misses any of them is refused with a {@link SchemaBootException} that names every unmet
 * prerequisite and the remedy; nothing has been created at that point.
 * </p>
 */
final class PostgresSearchDatabasePrerequisites {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSearchDatabasePrerequisites.class);

    /**
     * The connected database's name, encoding, {@code LC_CTYPE} and whether the trigram collation exists for its
     * encoding — the same expressions as the "check your database" query of the installation guide.
     */
    static final String DATABASE_SQL = "SELECT current_database(), pg_encoding_to_char(d.encoding), d.datctype, "
            + "EXISTS (SELECT 1 FROM pg_collation c WHERE c.collname = '"
            + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION + "' AND c.collencoding IN (-1, d.encoding)) "
            + "FROM pg_database d WHERE d.datname = current_database()";

    private static final String REQUIRED_ENCODING = "UTF8";

    private PostgresSearchDatabasePrerequisites() {
        throw new AssertionError();
    }

    /**
     * Reads the facts of the database the given factory connects to and refuses it if it misses a prerequisite. Lazy:
     * every subscription opens its own connection (the schema bootstrap subscribes once per attempt).
     *
     * @param ddlConnectionFactory the search schema's DDL connection factory.
     * @return completes when the database meets every prerequisite; fails with a {@link SchemaBootException} naming
     * every unmet prerequisite and the remedy.
     */
    static Mono<Void> verify(final ConnectionFactory ddlConnectionFactory) {
        return Mono.defer(() -> Mono.usingWhen(Mono.from(ddlConnectionFactory.create()),
                        PostgresSearchDatabasePrerequisites::read,
                        Connection::close))
                .flatMap(facts -> {
                    final Optional<String> refusal = refusal(facts);
                    if (refusal.isPresent()) {
                        return Mono.error(new SchemaBootException(refusal.get()));
                    }
                    LOGGER.debug("PostgreSQL search database '{}' (encoding {}, LC_CTYPE {}) has the collation "
                                    + "\"{}\" and a UTF-8 ctype.", facts.database(), facts.encoding(), facts.ctype(),
                            PostgresSearchSchema.TRIGRAM_INDEX_COLLATION);
                    return Mono.empty();
                });
    }

    private static Mono<Facts> read(final Connection connection) {
        return Flux.from(connection.createStatement(DATABASE_SQL).execute())
                .flatMap(result -> result.map((row, meta) -> new Facts(
                        row.get(0, String.class),
                        row.get(1, String.class),
                        row.get(2, String.class),
                        Boolean.TRUE.equals(row.get(3, Boolean.class)))))
                .single();
    }

    /**
     * @param facts the database facts.
     * @return the refusal message, or empty if the database meets every prerequisite.
     */
    static Optional<String> refusal(final Facts facts) {
        final List<String> unmet = unmetPrerequisites(facts);
        return unmet.isEmpty() ? Optional.empty() : Optional.of(refusalMessage(facts, unmet));
    }

    /**
     * @param facts the database facts.
     * @return the unmet prerequisites, in the order encoding, collation, ctype; empty if there is none.
     */
    static List<String> unmetPrerequisites(final Facts facts) {
        final List<String> unmet = new ArrayList<>();
        if (!REQUIRED_ENCODING.equals(facts.encoding())) {
            unmet.add("the encoding is " + facts.encoding() + ", not " + REQUIRED_ENCODING);
        }
        if (!facts.trigramCollationExists()) {
            unmet.add("the collation \"" + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION
                    + "\" does not exist for encoding " + facts.encoding());
        }
        if (!isUtf8Ctype(facts.ctype())) {
            unmet.add("LC_CTYPE '" + facts.ctype() + "' is not UTF-8 based");
        }
        return unmet;
    }

    /**
     * @param ctype a locale name as in {@code pg_database.datctype}, {@code language[_territory][.codeset][@modifier]}.
     * @return whether its codeset is UTF-8 ({@code C} and {@code POSIX} have none).
     */
    static boolean isUtf8Ctype(final String ctype) {
        final int dot = ctype.indexOf('.');
        if (dot < 0) {
            return false;
        }
        final int at = ctype.indexOf('@', dot);
        final String codeset = at < 0 ? ctype.substring(dot + 1) : ctype.substring(dot + 1, at);
        return "utf8".equals(codeset.replace("-", "").toLowerCase(Locale.ROOT));
    }

    private static String refusalMessage(final Facts facts, final List<String> unmet) {
        return "PostgreSQL search schema bootstrap failed: database '" + facts.database() + "' (encoding "
                + facts.encoding() + ", LC_CTYPE " + facts.ctype() + ") cannot host the search schema: "
                + String.join("; ", unmet) + ". The sf_trgm trigram index behind like()/ilike() needs the libc "
                + "collation \"" + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION + "\", which exists only in "
                + "UTF8-encoded databases on a server whose OS provides the C.UTF-8 locale. pg_trgm also lower-cases "
                + "and splits words with the database's LC_CTYPE, so on a C/POSIX or other non-UTF-8 LC_CTYPE ilike() "
                + "silently misses matches (e.g. U+0130 or U+212A in a value against i or k in the pattern) whenever "
                + "the planner uses that index. Create the search database with UTF8 encoding and a UTF-8 ctype, e.g. "
                + "CREATE DATABASE " + facts.database() + " TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' "
                + "LC_CTYPE 'C.UTF-8'; on CloudNativePG, set bootstrap.initdb.localeCType: C.UTF-8 (the default "
                + "encoding is UTF8).";
    }

    /**
     * What the check decides on.
     *
     * @param database {@code current_database()}.
     * @param encoding the database encoding, e.g. {@code UTF8}.
     * @param ctype {@code pg_database.datctype}.
     * @param trigramCollationExists whether the trigram collation exists for the database encoding.
     */
    record Facts(String database, String encoding, String ctype, boolean trigramCollationExists) {}

}
