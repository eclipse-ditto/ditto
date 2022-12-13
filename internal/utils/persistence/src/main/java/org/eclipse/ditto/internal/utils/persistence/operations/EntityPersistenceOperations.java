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
package org.eclipse.ditto.internal.utils.persistence.operations;

import java.util.Collection;
import java.util.List;

import org.eclipse.ditto.base.model.entity.id.EntityId;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Persistence ops on entities.
 * <p>
 * Currently the only supported operation is 'purge'.
 * </p>
 */
public interface EntityPersistenceOperations {

    /**
     * Purge an entity by the given {@code entityId}.
     *
     * @param entityId the ID of the entity to delete.
     * @return source of any errors during the purge.
     */
    Source<List<Throwable>, NotUsed> purgeEntity(EntityId entityId);

    /**
     * Purge all entities contained in the given {@code entityIds}.
     *
     * @param entityIds the IDs of the entities to delete
     * @return source of any errors during the purge.
     */
    default Source<List<Throwable>, NotUsed> purgeEntities(final Collection<EntityId> entityIds) {
        Source<List<Throwable>, NotUsed> result = Source.empty();

        for (final EntityId entityId : entityIds) {
            result = result.merge(purgeEntity(entityId));
        }

        return result;
    }

}
