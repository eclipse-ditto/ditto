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
import java.util.function.Consumer;

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
     * Creates an equality validator as {@code Consumer} accepting {@code EntityIdWithType} instances comparing them to
     * the passed in {@code expectedEntityId}.
     * <p>
     * When the entity IDs don't match, the an {@link IllegalArgumentException} will be thrown to the one providing the
     * entity id to compare with.
     * </p>
     * The equality validator will for {@code NamespacedEntityIdWithType} IDs compare equality of the IDs {@code name}s
     * excluding the {@code namespace} part as the {@code namespace} part might not yet be available.
     *
     * @param <I> the type of EntityIdWithType which may also be NamespacedEntityIdWithType.
     * @param expectedEntityId the expected entity ID.
     * @return a consumer accepting an instance of {@code <I>} which gets compared with {@code expectedEntityId} - if
     * the IDs are not equal, an {@link IllegalArgumentException} will be thrown, if they are equal, no other side
     * effect happens.
     * @throws NullPointerException if {@code expectedEntityId} is {@code null}.
     * @deprecated as of 1.2.0 please use {@link #isCompatibleOrThrow(EntityIdWithType)} instead.
     */
    @Deprecated
    public static <I extends EntityIdWithType> Consumer<I> createEqualityValidator(final I expectedEntityId) {
        return expectedEntityId::isCompatibleOrThrow;
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
