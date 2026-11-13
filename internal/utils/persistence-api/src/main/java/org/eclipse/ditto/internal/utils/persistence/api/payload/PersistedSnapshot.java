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
package org.eclipse.ditto.internal.utils.persistence.api.payload;

import org.eclipse.ditto.json.JsonObject;

/**
 * A persisted snapshot as streamed by {@link PersistedPayloadStore#streamSnapshots}: its position in the stream, its
 * persistence id and its payload rendered as JSON. The entry keeps the backend's handle on the stored snapshot (for
 * example, on MongoDB the whole snapshot document), so that a {@link #withPayload(JsonObject) rewritten} entry can be
 * written back in place of the stored one by {@link PersistedPayloadStore#rewriteSnapshots}.
 *
 * @since 4.0.0
 */
public interface PersistedSnapshot {

    /**
     * Returns the opaque, backend-defined cursor of this snapshot: what a caller records as "last processed" and passes
     * back as {@code afterCursor} to continue the stream behind it.
     *
     * @return the cursor.
     */
    String cursor();

    /**
     * @return the persistence id of the entity the snapshot belongs to.
     */
    String pid();

    /**
     * Returns the snapshot payload as JSON.
     *
     * @return the payload.
     * @throws IllegalStateException if the stored snapshot holds no payload.
     * @throws RuntimeException if the stored payload cannot be rendered as JSON; a caller that processes snapshots one
     * by one catches it and counts the snapshot as failed.
     */
    JsonObject payload();

    /**
     * Returns this snapshot with another payload: same cursor, same pid, same backend handle, the given payload stored
     * in the backend's representation. This entry is left unchanged.
     *
     * @param payload the new payload.
     * @return the snapshot with the new payload, ready for {@link PersistedPayloadStore#rewriteSnapshots}.
     * @throws NullPointerException if {@code payload} is {@code null}.
     */
    PersistedSnapshot withPayload(JsonObject payload);

}
