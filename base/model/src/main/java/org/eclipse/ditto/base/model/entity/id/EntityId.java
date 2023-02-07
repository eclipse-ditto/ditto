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
package org.eclipse.ditto.base.model.entity.id;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Java representation of an Entity ID.
 */
@Immutable
public interface EntityId extends CharSequence, Comparable<EntityId> {

    /**
     * Instantiates a {@code org.eclipse.ditto.base.model.entity.id.EntityId} based on the given entity type and
     * entity ID.
     *
     * @param entityType the type of the entity which is identified by the given ID.
     * @param entityId the ID of the entity.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws EntityIdInvalidException if {@code entityIdValue} represents an invalid ID for {@code entityType}.
     * @since 2.0.0
     */
    static EntityId of(final EntityType entityType, final CharSequence entityId) {
        final EntityIds entityIds = EntityIds.getInstance();
        try {
            // most entity ids are namespaces, so try that first
            return entityIds.getNamespacedEntityId(entityType, entityId);
        } catch (final NamespacedEntityIdInvalidException namespacedEntityIdInvalidException) {
            // only in the exceptional case, fall back to non-namespaced flavor:
            return entityIds.getEntityId(entityType, entityId);
        }
    }

    @Override
    default int length() {
        return toString().length();
    }

    @Override
    default char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    default CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    /**
     * Compares the entity IDs based on their String representation.
     *
     * @param o the other entity ID.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
     * the specified object.
     */
    @Override
    default int compareTo(final EntityId o) {
        return toString().compareTo(o.toString());
    }

    /**
     * Returns the entity type.
     *
     * @return the entity type.
     * @since 2.0.0
     */
    EntityType getEntityType();

}
