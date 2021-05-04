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
package org.eclipse.ditto.base.model.entity.id;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.type.EntityType;


/**
 * Abstract base class for instances of @{code EntityId}.
 *
 * @since 2.0.0
 */
public abstract class AbstractEntityId implements EntityId {

    private final EntityType entityType;
    private final String id;

    /**
     * Constructs a new AbstractEntityId object.
     *
     * @param entityType the entity type.
     * @param id the id of the entity.
     * @throws NullPointerException if {@code id} or {@code entityType} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    protected AbstractEntityId(final EntityType entityType, final CharSequence id) {
        this.entityType = checkNotNull(entityType, "entityType");
        this.id = argumentNotEmpty(id, "entityId").toString();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractEntityId that = (AbstractEntityId) o;
        return Objects.equals(id, that.id) && Objects.equals(entityType, that.entityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, id);
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public EntityType getEntityType() {
        return entityType;
    }

}
