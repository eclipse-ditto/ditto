/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.concierge;

/**
 * Entity ID together with resource type.
 */
public interface EntityId {

    /**
     * Retrieve the resource type.
     *
     * @return the resource type.
     */
    String getResourceType();

    /**
     * Retrieve the ID.
     *
     * @return the ID.
     */
    String getId();

    /**
     * Serialize this object as string.
     *
     * @return serialized form of this object.
     */
    String toString();

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param resourceType the resource type.
     * @param id the entity ID.
     * @return the entity ID with resource type object.
     */
    static EntityId of(final String resourceType, final String id) {
        return ConciergeModelFactory.newEntityId(resourceType, id);
    }

    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    static EntityId readFrom(final String string) {
        return ConciergeModelFactory.readEntityIdFrom(string);
    }
}
