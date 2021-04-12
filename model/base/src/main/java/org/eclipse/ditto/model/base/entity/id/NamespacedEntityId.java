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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Interface for all entity IDs that contain a namespace in their string representation.
 * Every implementation of this interface needs to ensure that name and namespace are valid according to
 * {@link org.eclipse.ditto.model.base.entity.id.RegexPatterns#ENTITY_NAME_REGEX} and
 * {@link org.eclipse.ditto.model.base.entity.id.RegexPatterns#NAMESPACE_REGEX}.
 * Every implementation must ensure immutability.
 */
@Immutable
public interface NamespacedEntityId extends EntityId {

    /**
     * Gets the name part of this entity ID.
     *
     * @return the name if the entity.
     */
    String getName();

    /**
     * Gets the namespace part of this entity ID.
     *
     * @return the namespace o the entity.
     */
    String getNamespace();


    /**
     * Checks if the passed entity ID is compatible with this entity ID.
     * The entity IDs are regarded as compatible if they are equal to each other as defined in equals.
     * Furthermore they are compatible if they have the same names while the namespace of each entity ID may be empty.
     * This could be the case for example for "Create Thing" commands where the default namespace is added at a later
     * step.
     * If the namespaces of both compared entity IDs are not empty, they have to be equal.
     * The entity types always have to be equal.
     *
     * @param otherEntityId the entity ID to be compared for equality with this entity ID.
     * @return {@code true} if {@code otherEntityId} is compatible with this entity ID {@code false} otherwise.
     * @since 1.2.0
     */
    @Override
    default boolean isCompatible(@Nullable final EntityId otherEntityId) {
        final boolean result;
        if (null == otherEntityId) {
            result = false;
        } else if (equals(otherEntityId)) {
            result = true;
        } else if (!Objects.equals(getEntityType(), otherEntityId.getEntityType())) {
            result = false;
        } else if (otherEntityId instanceof AbstractNamespacedEntityId) {
            result = isNamespaceAndNameCompatible((NamespacedEntityId) otherEntityId);
        } else {
            result = false;
        }
        return result;
    }

    /**
     * Indicates whether the given other entity ID has the same namespace and same name like this entity id.
     * The empty namespace will always be considered equal to another namespace.
     *
     * @param otherEntityId the other entity ID.
     * @return true if both IDs have equal namespaces and names, false otherwise.
     */
    default boolean isNamespaceAndNameCompatible(final NamespacedEntityId otherEntityId) {
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

}
