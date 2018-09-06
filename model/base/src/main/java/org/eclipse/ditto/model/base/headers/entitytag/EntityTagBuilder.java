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
package org.eclipse.ditto.model.base.headers.entitytag;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.base.entity.Revision;

/**
 * Responsible for creating the an {@link EntityTag} based on a given entity.
 */
final class EntityTagBuilder {

    private static final String TOP_LEVEL_ENTITY_PREFIX = "rev:";
    private static final String SUB_RESOURCE_PREFIX = "hash:";

    private EntityTagBuilder() {}

    /**
     * Generates an {@link EntityTag} for the given entity.
     * For Classes that extends {@link Entity} the revision will be the opaque-tag.
     * For all other objects the hashcode of the object will be used as opaque-tag.
     *
     * @param entity The entity you want to get an {@link EntityTag} for.
     * @return An optional of the generated {@link EntityTag}. If no value could be generated the optional is empty.
     */
    static Optional<EntityTag> buildFromEntity(@Nullable Object entity) {
        if (entity == null) {
            return Optional.empty();
        }

        if (entity instanceof Entity) {
            return buildForTopLevelEntity((Entity<? extends Revision>) entity);
        } else {
            return buildForSubEntity(entity);
        }
    }

    private static Optional<EntityTag> buildForTopLevelEntity(final Entity<? extends Revision> topLevelEntity) {
        if (topLevelEntity.isDeleted()) {
            return Optional.empty();
        }

        return topLevelEntity.getRevision()
                .map(Revision::toString)
                .map(value -> TOP_LEVEL_ENTITY_PREFIX + value)
                .map(EntityTagBuilder::enquote)
                .map(EntityTag::strong);
    }

    private static Optional<EntityTag> buildForSubEntity(final Object object) {
        return Optional.of(object.hashCode())
                .map(Integer::toHexString)
                .map(value -> SUB_RESOURCE_PREFIX + value)
                .map(EntityTagBuilder::enquote)
                .map(EntityTag::strong);
    }

    private static String enquote(final String stringToPutInQuotes) {
        return "\"" + stringToPutInQuotes + "\"";
    }
}
