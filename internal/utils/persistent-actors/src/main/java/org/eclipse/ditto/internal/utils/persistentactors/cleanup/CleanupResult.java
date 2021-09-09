/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.mongodb.client.result.DeleteResult;

@Immutable
final class CleanupResult {

    final Type type;
    final SnapshotRevision snapshotRevision;
    final DeleteResult result;

    CleanupResult(final Type type, final SnapshotRevision snapshotRevision, final DeleteResult result) {
        this.type = type;
        this.snapshotRevision = snapshotRevision;
        this.result = result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[type=" + type +
                ",snapshotRevision=" + snapshotRevision +
                ",result=" + result +
                "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, snapshotRevision, result);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CleanupResult) {
            final var that = (CleanupResult) other;
            return type == that.type && Objects.equals(snapshotRevision, that.snapshotRevision) && Objects.equals(result, that.result);
        } else {
            return false;
        }
    }

    enum Type {
        EVENTS,
        SNAPSHOTS
    }
}
