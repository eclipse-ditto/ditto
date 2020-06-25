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

    /**
     * Checks if the passed entity ID is compatible with this entity ID.
     * The entity IDs are regarded as compatible if they are equal to each other as defined in {@link #equals(Object)}.
     * Furthermore they are compatible if they have the same names while the namespace of each entity ID may be empty.
     * This could be the case for example for "Create Thing" commands where the default namespace is added at a later
     * step.
     * If the namespaces of both compared entity IDs are not empty, they have to be equal.
     * The entity types always have to be equal.
     *
     * @param otherEntityId the entity ID to be compared for equality with this entity ID.
     * @throws IllegalArgumentException if {@code otherEntityId} is not compatible with this entity ID.
     * @return {@code true} if {@code otherEntityId} is compatible with this entity ID.
     * @since 1.2.0
     */
    @Override
    public boolean isCompatibleOrThrow(@Nullable final EntityIdWithType otherEntityId) {
        final boolean result;
        if (null == otherEntityId) {
            result = false;
        } else if (equals(otherEntityId)) {
            result = true;
        } else if (!Objects.equals(getEntityType(), otherEntityId.getEntityType())) {
            result = false;
        } else if (otherEntityId instanceof NamespacedEntityIdWithType) {
            result = isNamespaceAndNameCompatible((NamespacedEntityId) otherEntityId);
        } else {
            result = false;
        }
        if (!result) {
            throw getIllegalArgumentExceptionForDifferentEntityIds(otherEntityId);
        }
        return result;
    }

    private boolean isNamespaceAndNameCompatible(final NamespacedEntityId otherEntityId) {
        final boolean result;
        if (Objects.equals(getName(), otherEntityId.getName())) {
            final String namespace = getNamespace();
            final String otherNamespace = otherEntityId.getNamespace();
            if (namespace.equals(otherNamespace)) {
                result = true;
            } else {
                result = namespace.isEmpty() || otherNamespace.isEmpty();
            }
        } else {
            result = false;
        }
        return result;
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
        return Objects.equals(namespacedEntityId, that.namespacedEntityId) &&
                Objects.equals(getEntityType(), that.getEntityType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespacedEntityId, getEntityType());
    }

}
