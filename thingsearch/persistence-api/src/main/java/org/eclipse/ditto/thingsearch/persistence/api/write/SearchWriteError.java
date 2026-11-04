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
package org.eclipse.ditto.thingsearch.persistence.api.write;

/**
 * Backend-neutral per-element error of a search write batch.
 *
 * @param index the 0-based index of the failing write model within the submitted batch.
 * @param category the backend-neutral error category (duplicate-key errors are treated as success upstream).
 * @param message a human-readable error message.
 * @since 4.0.0
 */
public record SearchWriteError(int index, Category category, String message) {

    /**
     * Backend-neutral classification of a write error, preserving the duplicate-key business rule.
     */
    public enum Category {

        /** A duplicate-key violation — treated as success by the acknowledgement flow. */
        DUPLICATE_KEY,

        /** Any other error category. */
        OTHER
    }
}
