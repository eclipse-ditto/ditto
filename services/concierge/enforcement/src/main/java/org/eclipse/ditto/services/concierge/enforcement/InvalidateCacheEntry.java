/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.Objects;

import org.eclipse.ditto.services.models.concierge.EntityId;

/**
 * TODO TJ javadoc
 *
 * TODO TJ add serialization
 */
public final class InvalidateCacheEntry {

    private final EntityId entityId;

    public InvalidateCacheEntry(final EntityId entityId) {this.entityId = entityId;}

    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvalidateCacheEntry)) {
            return false;
        }
        final InvalidateCacheEntry that = (InvalidateCacheEntry) o;
        return Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                "]";
    }
}
