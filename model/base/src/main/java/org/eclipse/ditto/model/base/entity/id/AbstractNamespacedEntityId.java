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

import org.eclipse.ditto.model.base.entity.type.EntityType;

/**
 * Base implementation for namespaced entity IDs which are aware of their entity type.
 * <em>Subclasses are required to be immutable.</em>
 *
 * @since 1.1.0
 */
@Immutable
public abstract class AbstractNamespacedEntityId implements NamespacedEntityId {

    private final NamespacedEntityId namespacedEntityId;

    /**
     * Constructs a new NamespacedEntityId object.
     *
     * @param namespacedEntityId the entity ID to delegate to.
     * @throws NullPointerException if {@code namespacedEntityId} is {@code null}.
     */
    protected AbstractNamespacedEntityId(final NamespacedEntityId namespacedEntityId) {
        this.namespacedEntityId = checkNotNull(namespacedEntityId, "namespacedEntityId");
    }

    @Override
    public String getNamespace() {
        return namespacedEntityId.getNamespace();
    }

    @Override
    public String getName() {
        return namespacedEntityId.getName();
    }

    @Override
    public EntityType getEntityType() {
        return namespacedEntityId.getEntityType();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractNamespacedEntityId that = (AbstractNamespacedEntityId) o;
        return Objects.equals(namespacedEntityId, that.namespacedEntityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespacedEntityId);
    }

    @Override
    public String toString() {
        return namespacedEntityId.toString();
    }

}
