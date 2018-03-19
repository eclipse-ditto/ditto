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
package org.eclipse.ditto.services.models.authorization;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Implementation of {@code EntityId}.
 */
@Immutable
final class ImmutableEntityId implements EntityId {

    private static final String DELIMITER = ":";

    private final String resourceType;
    private final String id;

    /**
     * Creates a new {@code ImmutableEntityId}.
     *
     * @param resourceType the resource type.
     * @param id the entity id.
     * @throws IllegalArgumentException if resource type contains ':'.
     */
    ImmutableEntityId(final String resourceType, final String id) {
        this.resourceType = checkNotNull(resourceType, "resourceType");
        this.id = checkNotNull(id, "id");
        if (resourceType.contains(DELIMITER)) {
            final String message =
                    String.format("Resource type <%s> may not contain ':'. Id = <%s>", resourceType, id);
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof ImmutableEntityId) {
            final ImmutableEntityId that = (ImmutableEntityId) o;
            return Objects.equals(resourceType, that.resourceType) && Objects.equals(id, that.id);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, id);
    }

    @Override
    public String toString() {
        return String.format("%s%s%s", resourceType, DELIMITER, id);
    }

    static ImmutableEntityId fromString(final String string) {
        final int delimiterIndex = string.indexOf(DELIMITER);
        if (delimiterIndex < 0) {
            final String message = String.format("Unexpected EntityId format: <%s>", string);
            throw new IllegalArgumentException(message);
        } else {
            final String id = string.substring(delimiterIndex + 1);
            final String resourceType = string.substring(0, delimiterIndex);
            return new ImmutableEntityId(resourceType, id);
        }
    }
}
