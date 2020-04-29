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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Base implementation for namespaced entity IDs which are aware of their entity type.
 * <em>Subclasses are required to be immutable.</em>
 *
 * @since 1.1.0
 */
@Immutable
public abstract class NamespacedEntityIdWithType extends EntityIdWithType implements NamespacedEntityId {

    private final NamespacedEntityId namespacedEntityId;

    /**
     * Constructs a new NamespacedEntityIdWithType object.
     *
     * @param namespacedEntityId the entity ID to delegate to.
     * @throws NullPointerException if {@code namespacedEntityId} is {@code null}.
     */
    protected NamespacedEntityIdWithType(final NamespacedEntityId namespacedEntityId) {
        super(namespacedEntityId);
        this.namespacedEntityId = namespacedEntityId;
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
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final NamespacedEntityIdWithType that = (NamespacedEntityIdWithType) o;
        return Objects.equals(namespacedEntityId, that.namespacedEntityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespacedEntityId);
    }

}
