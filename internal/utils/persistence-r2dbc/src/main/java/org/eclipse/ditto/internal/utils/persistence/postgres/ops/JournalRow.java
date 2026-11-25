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

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A single row of a {@code <e>_journal} table as loaded by {@link PostgresPersistenceOperations}: the persistence id, the
 * sequence number, the global monotonic {@code seq} IDENTITY offset, the Pekko manifest (event type
 * FQN), the tag array and the raw JSONB event text.
 *
 * @param pid the persistence id.
 * @param sequenceNr the per-pid sequence number ({@code sn}).
 * @param seq the global monotonic IDENTITY offset, used as the {@code EventsByTag} stream offset.
 * @param manifest the Pekko manifest (event type FQN).
 * @param tags the tag array.
 * @param eventJson the raw JSONB event value (JSON text).
 */
@Immutable
public record JournalRow(String pid, long sequenceNr, long seq, @Nullable String manifest, List<String> tags,
                         String eventJson) {

    public JournalRow {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
