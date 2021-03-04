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
package org.eclipse.ditto.model.base.entity.id;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Default implementation of an entity ID.
 */
@Immutable
public final class DefaultEntityId implements EntityId {

    private static final DefaultEntityId DUMMY_ID = DefaultEntityId.of(":_");

    private final String id;

    private DefaultEntityId(final CharSequence id) {
        this.id = argumentNotEmpty(id, "entityId").toString();
    }

    /**
     * Returns an instance of this class based on the given entity ID.
     * May return the argument itself if it is already a DefaultEntityId.
     *
     * @param entityId the entity ID.
     * @return the Entity ID instance.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    public static DefaultEntityId of(final CharSequence entityId) {
        if (entityId instanceof DefaultEntityId) {
            return (DefaultEntityId) entityId;
        }
        return new DefaultEntityId(entityId);
    }

    /**
     * Returns a randomly generated unique entity ID.
     *
     * @return the generated entity ID.
     */
    public static DefaultEntityId generateRandom() {
        return new DefaultEntityId(UUID.randomUUID().toString());
    }

    /**
     * Returns a dummy {@link EntityId}.
     * This ID should not be used.
     * It can be identified by checking {@link EntityId#isDummy()}.
     *
     * @return the dummy ID.
     */
    public static DefaultEntityId dummy() {
        return DUMMY_ID;
    }

    @Override
    public boolean isDummy() {
        return DUMMY_ID.equals(this);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultEntityId that = (DefaultEntityId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }

}
