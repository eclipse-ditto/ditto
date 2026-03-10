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
package org.eclipse.ditto.connectivity.service.messaging.persistence.migration;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Immutable configuration context for encryption migration operations.
 * <p>
 * Encapsulates the encryption keys, JSON pointers to encrypt/decrypt, and the entity type prefix
 * required for processing connection snapshots and journal events.
 *
 * @param oldKey the old encryption key, or {@code null} for initial encryption (plaintext data)
 * @param newKey the new encryption key, or {@code null} to disable encryption (write plaintext)
 * @param pointers the JSON pointers to encrypt/decrypt (e.g., "/uri", "/credentials/password")
 * @param entityTypePrefix the entity type prefix applied to pointers (e.g., "connection" for journal events, "" for snapshots)
 */
public record MigrationContext(
        @Nullable String oldKey,
        @Nullable String newKey,
        List<String> pointers,
        String entityTypePrefix
) {

    /**
     * Creates a migration context for snapshot processing.
     * <p>
     * Snapshots use an empty entity type prefix, so pointers are applied directly
     * (e.g., "/uri" targets the "/uri" field in the snapshot JSON).
     *
     * @param oldKey the old encryption key, or {@code null} for initial encryption
     * @param newKey the new encryption key, or {@code null} to disable encryption
     * @param pointers the JSON pointers to encrypt/decrypt
     * @return a migration context for snapshots
     */
    public static MigrationContext forSnapshots(@Nullable final String oldKey, @Nullable final String newKey,
            final List<String> pointers) {
        return new MigrationContext(oldKey, newKey, pointers, "");
    }

    /**
     * Creates a migration context for journal event processing.
     * <p>
     * Journal events use "connection" as the entity type prefix, so pointers are prefixed
     * (e.g., "/uri" targets the "/connection/uri" field in the journal event JSON).
     *
     * @param oldKey the old encryption key, or {@code null} for initial encryption
     * @param newKey the new encryption key, or {@code null} to disable encryption
     * @param pointers the JSON pointers to encrypt/decrypt
     * @return a migration context for journal events
     */
    public static MigrationContext forJournal(@Nullable final String oldKey, @Nullable final String newKey,
            final List<String> pointers) {
        return new MigrationContext(oldKey, newKey, pointers, "connection");
    }
}
