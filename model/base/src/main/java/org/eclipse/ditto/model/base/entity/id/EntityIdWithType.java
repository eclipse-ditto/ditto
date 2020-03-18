/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.entity.id;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.type.WithEntityType;

/**
 * Base implementation for entity IDs which are aware of their entity type.
 * <em>Subclasses are required to be immutable.</em>
 *
 * @since 1.1.0
 */
@Immutable
public abstract class EntityIdWithType implements EntityId, WithEntityType {

    private final EntityId entityId;

    protected EntityIdWithType(final EntityId entityId) {
        this.entityId = checkNotNull(entityId, "entityId");
    }

    @Override
    public boolean isDummy() {
        return entityId.isDummy();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityIdWithType that = (EntityIdWithType) o;
        return Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }

    @Override
    public String toString() {
        return entityId.toString();
    }

}
