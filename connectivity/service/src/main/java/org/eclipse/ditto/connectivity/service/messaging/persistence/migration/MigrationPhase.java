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

import java.util.Optional;

/**
 * Represents the phases of the encryption migration process.
 * <p>
 * The migration progresses through phases in order:
 * <ol>
 *   <li>{@link #SNAPSHOTS} - Processing snapshot documents</li>
 *   <li>{@link #JOURNAL} - Processing journal documents</li>
 *   <li>{@link #COMPLETED} - Migration finished successfully</li>
 * </ol>
 * Additionally, phases can be marked as aborted using the {@code aborted:} prefix.
 */
public enum MigrationPhase {

    /**
     * Processing snapshot documents (connection_snaps collection).
     */
    SNAPSHOTS("snapshots"),

    /**
     * Processing journal documents (connection_journal collection).
     */
    JOURNAL("journal"),

    /**
     * Migration completed successfully.
     */
    COMPLETED("completed");

    private static final String ABORTED_PREFIX = "aborted:";

    private final String value;

    MigrationPhase(final String value) {
        this.value = value;
    }

    /**
     * Returns the string value of this phase for serialization.
     *
     * @return the string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates an aborted phase string with this phase as the context.
     * <p>
     * For example, {@code SNAPSHOTS.toAbortedString()} returns {@code "aborted:snapshots"}.
     *
     * @return the aborted phase string
     */
    public String toAbortedString() {
        return ABORTED_PREFIX + value;
    }

    /**
     * Creates an in-progress phase string with this phase as the context.
     * <p>
     * For example, {@code SNAPSHOTS.toInProgressString()} returns {@code "in_progress:snapshots"}.
     *
     * @return the in-progress phase string
     */
    public String toInProgressString() {
        return "in_progress:" + value;
    }

    /**
     * Parses a string value to a MigrationPhase.
     * <p>
     * Handles raw phase values (e.g., "snapshots"), aborted phases (e.g., "aborted:snapshots"),
     * and in-progress phases (e.g., "in_progress:journal").
     *
     * @param value the string value to parse
     * @return an Optional containing the phase if found, empty otherwise
     */
    public static Optional<MigrationPhase> fromValue(final String value) {
        if (value == null) {
            return Optional.empty();
        }
        final String normalizedValue = stripPrefix(value);
        for (final MigrationPhase phase : values()) {
            if (phase.value.equals(normalizedValue)) {
                return Optional.of(phase);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the given string represents an aborted phase.
     *
     * @param value the string value to check
     * @return true if the value starts with the aborted prefix
     */
    public static boolean isAborted(final String value) {
        return value.startsWith(ABORTED_PREFIX);
    }

    /**
     * Returns the aborted prefix constant.
     *
     * @return the aborted prefix
     */
    public static String getAbortedPrefix() {
        return ABORTED_PREFIX;
    }

    /**
     * Strips the "aborted:" or "in_progress:" prefix from a phase string.
     *
     * @param value the value to strip
     * @return the phase value without prefix
     */
    public static String stripPrefix(final String value) {
        if (value.startsWith(ABORTED_PREFIX)) {
            return value.substring(ABORTED_PREFIX.length());
        }
        if (value.startsWith("in_progress:")) {
            return value.substring("in_progress:".length());
        }
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
