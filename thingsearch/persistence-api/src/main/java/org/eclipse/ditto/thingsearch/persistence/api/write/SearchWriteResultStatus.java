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
 * Backend-neutral outcome vocabulary of applying a batch of search write models, preserving the business
 * rules of the Mongo bulk-write acknowledgement flow.
 *
 * @since 4.0.0
 */
public enum SearchWriteResultStatus {

    /** The backend did not acknowledge the write (write concern not satisfied). */
    UNACKNOWLEDGED,

    /** The reported error indices are inconsistent with the submitted batch (a bug/corruption signal). */
    CONSISTENCY_ERROR,

    /** An incremental patch did not match the expected document; the index entry must be recomputed in full. */
    INCORRECT_PATCH,

    /** A genuine (non duplicate-key) write error occurred. */
    WRITE_ERROR,

    /** The batch was applied successfully (duplicate-key errors count as success). */
    OK
}
