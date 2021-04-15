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

import java.text.MessageFormat;
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

    /**
     * Checks if the passed entity ID is compatible with this entity ID.
     * In the base implementation a given entity ID is compatible if a call of {@link #equals(Object)} with that ID
     * would yield {@code true}.
     * Subclasses may implement a different behavior.
     * Please have a look at the documentation of the subclass for further information.
     *
     * @param otherEntityId the entity ID to be compared for equality with this entity ID.
     * @throws IllegalArgumentException if {@code otherEntityId} is not compatible with this entity ID.
     * @return {@code true} if {@code otherEntityId} is compatible with this entity ID.
     * @since 1.2.0
     */
    public boolean isCompatibleOrThrow(@Nullable final EntityIdWithType otherEntityId) {
        if (!equals(otherEntityId)) {
            throw getIllegalArgumentExceptionForDifferentEntityIds(otherEntityId);
        }
        return true;
    }

    protected IllegalArgumentException getIllegalArgumentExceptionForDifferentEntityIds(
            @Nullable final EntityIdWithType actual) {

        final String pattern = "The entity ID <{0}> is not compatible with <{1}>!";
        return new IllegalArgumentException(MessageFormat.format(pattern, actual, this));
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
        return Objects.equals(entityId, that.entityId) && Objects.equals(getEntityType(), that.getEntityType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getEntityType());
    }

    @Override
    public String toString() {
        return entityId.toString();
    }

}
