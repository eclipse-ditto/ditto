/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.entitytag;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.entity.Revision;

/**
 * Responsible for creating the an {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} based on a given entity.
 */
final class EntityTagBuilder {

    private static final String TOP_LEVEL_ENTITY_PREFIX = "rev:";
    private static final String SUB_RESOURCE_PREFIX = "hash:";

    private EntityTagBuilder() {}

    /**
     * Generates an {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} for the given entity.
     * For Classes that extends {@link org.eclipse.ditto.base.model.entity.Entity} the revision will be the opaque-tag.
     * For all other objects the hashcode of the object will be used as opaque-tag.
     *
     * @param entity The entity you want to get an {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} for.
     * @return An optional of the generated {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag}. If no value could be generated the optional is empty.
     */
    static Optional<EntityTag> buildFromEntity(@Nullable final Object entity) {
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
