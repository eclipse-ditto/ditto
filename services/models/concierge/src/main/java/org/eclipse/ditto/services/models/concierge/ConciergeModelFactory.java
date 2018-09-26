/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.concierge;

import javax.annotation.concurrent.Immutable;

/**
 * Creates model instances for the Concierge Service.
 */
@Immutable
public final class ConciergeModelFactory {

    private ConciergeModelFactory() {
        throw new AssertionError();
    }

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param resourceType the resource type.
     * @param id the entity ID.
     * @return the entity ID with resource type object.
     */
    public static EntityId newEntityId(final String resourceType, final String id) {
        return ImmutableEntityId.of(resourceType, id);
    }

    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    public static EntityId readEntityIdFrom(final String string) {
        return ImmutableEntityId.readFrom(string);
    }
}
