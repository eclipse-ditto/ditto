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

@Immutable
final class SnapshotRevision {

    final String pid;
    final long sn;
    final boolean isDeleted;

    SnapshotRevision(final String pid, final long sn, final boolean isDeleted) {
        this.pid = pid;
        this.sn = sn;
        this.isDeleted = isDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[pid=" + pid + ",sn=" + sn + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, sn, isDeleted);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof SnapshotRevision) {
            final var that = (SnapshotRevision) other;
            return Objects.equals(pid, that.pid) && sn == that.sn && isDeleted == that.isDeleted;
        } else {
            return false;
        }
    }
}
