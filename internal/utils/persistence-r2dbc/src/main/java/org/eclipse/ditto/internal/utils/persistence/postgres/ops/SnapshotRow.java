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

import java.time.Instant;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A single row of a {@code <e>_snaps} table as loaded by {@link PostgresPersistenceOperations}: the persistence id, the
 * sequence number, the lifecycle marker (e.g. {@code "DELETED"} or {@code null}), the {@code written_at} timestamp
 * (mapped from {@code SnapshotMetadata.timestamp}) and the raw JSONB snapshot text.
 *
 * @param pid the persistence id.
 * @param sequenceNr the snapshot sequence number.
 * @param lifecycle the lifecycle marker, or {@code null}.
 * @param writtenAt the {@code written_at} timestamp.
 * @param snapshotJson the raw JSONB snapshot value (JSON text), decoded by the snapshot adapter.
 */
@Immutable
public record SnapshotRow(String pid, long sequenceNr, @Nullable String lifecycle, Instant writtenAt,
                          String snapshotJson) {
}
