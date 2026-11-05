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
package org.eclipse.ditto.internal.utils.persistence.postgres.ops;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

/**
 * Resolves the three per-entity table names ({@code <e>_journal}, {@code <e>_journal_seq}, {@code <e>_snaps}) for a
 * single table-set prefix (one of {@link PostgresSchema#ENTITIES}: {@code things}, {@code policies},
 * {@code connections}, {@code wot}, and {@code connection_remember} for connectivity's remember-entities store).
 * <p>
 * Centralising this keeps the SQL in {@link PostgresPersistenceOperations} free of string concatenation and guarantees that
 * the journal, the high-water-mark metadata table and the snapshot table for an entity stay in lockstep
 * with the DDL emitted by {@link PostgresSchema}.
 * </p>
 */
@Immutable
public final class PostgresTableNames {

    private static final Set<String> KNOWN = Set.copyOf(PostgresSchema.ENTITIES);

    /**
     * Aliases from an entity-<em>type</em> spelling to its table <em>prefix</em> (one of {@link PostgresSchema#ENTITIES}).
     * <p>
     * The WoT validation config entity type is {@code "wot-validation-config"} (matching {@code WotValidationConfigId}'s
     * {@code @TypedEntityId} and the {@code wot-validation-config.{journal|snapshot}} keys in the Postgres profile), but
     * its tables use the compact prefix {@code "wot"} (per {@link PostgresSchema#ENTITIES} and the
     * {@code ditto-postgres-wot-*} plugin blocks). Without this alias, resolving the tables by the entity-type spelling
     * — as happens when {@code WotValidationConfigPersistenceActor} or the read journal carries that type — would throw
     * {@code IllegalArgumentException}. See round-2 review finding M-6.
     */
    private static final Map<String, String> ENTITY_TYPE_ALIASES = Map.of(
            "wot-validation-config", "wot");

    private final String entityPrefix;
    private final String journalTable;
    private final String journalSeqTable;
    private final String snapsTable;

    private PostgresTableNames(final String entityPrefix) {
        this.entityPrefix = entityPrefix;
        this.journalTable = entityPrefix + "_journal";
        this.journalSeqTable = entityPrefix + "_journal_seq";
        this.snapsTable = entityPrefix + "_snaps";
    }

    /**
     * @param entityPrefixOrType the entity table prefix (e.g. {@code things}, one of {@link PostgresSchema#ENTITIES}) or
     * a known entity-type alias (e.g. {@code wot-validation-config}, which resolves to the {@code wot} tables — M-6).
     * @return the resolved table names.
     * @throws IllegalArgumentException if the value is neither a known entity prefix nor a known alias.
     */
    public static PostgresTableNames of(final String entityPrefixOrType) {
        final String normalized = checkNotNull(entityPrefixOrType, "entityPrefixOrType").toLowerCase(Locale.ROOT);
        // Map an entity-type spelling (e.g. "wot-validation-config") onto its table prefix ("wot") before validating.
        final String prefix = ENTITY_TYPE_ALIASES.getOrDefault(normalized, normalized);
        if (!KNOWN.contains(prefix)) {
            throw new IllegalArgumentException(
                    "Unknown entity prefix <" + entityPrefixOrType + ">; expected one of " + KNOWN
                            + " or a known entity-type alias " + ENTITY_TYPE_ALIASES.keySet());
        }
        return new PostgresTableNames(prefix);
    }

    public String entityPrefix() {
        return entityPrefix;
    }

    public String journalTable() {
        return journalTable;
    }

    public String journalSeqTable() {
        return journalSeqTable;
    }

    public String snapsTable() {
        return snapsTable;
    }

}
