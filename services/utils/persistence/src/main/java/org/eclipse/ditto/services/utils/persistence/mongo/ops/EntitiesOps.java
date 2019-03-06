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
package org.eclipse.ditto.services.utils.persistence.mongo.ops;

import java.util.Collection;
import java.util.List;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Persistence ops on entities.
 * <p>
 * Currently the only supported operation is 'purge'.
 * </p>
 */
public interface EntitiesOps {

    /**
     * Purge an entity by the given {@code entityId}.
     *
     * @param entityId the ID of the entity to delete.
     * @return source of any errors during the purge.
     */
    Source<List<Throwable>, NotUsed> purgeEntity(CharSequence entityId);

    /**
     * Purge all entities contained in the given {@code entityIds}.
     *
     * @param entityIds the IDs of the entities to delete
     * @return source of any errors during the purge.
     */
    default Source<List<Throwable>, NotUsed> purgeEntities(final Collection<String> entityIds) {
        Source<List<Throwable>, NotUsed> result = Source.empty();

        for (final String entityId : entityIds) {
            result = result.merge(purgeEntity(entityId));
        }

        return result;
    }
}
