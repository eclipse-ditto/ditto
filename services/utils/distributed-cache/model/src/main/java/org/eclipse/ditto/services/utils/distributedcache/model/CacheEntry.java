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
package org.eclipse.ditto.services.utils.distributedcache.model;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * This interface represents a generic data structure used in cluster (via Akka Distributed Data) as cache for important
 * entity data. The cached data should be kept low because it uses memory in each cluster node and is synchronised in
 * the cluster all the time.
 * <p>
 * <em>NOTE:</em> Implementations are required to be immutable!
 * </p>
 */
@Immutable
public interface CacheEntry extends Jsonifiable<JsonObject> {

    /**
     * Returns the cached policy ID of the entity.
     *
     * @return the policy ID or an empty Optional.
     */
    Optional<String> getPolicyId();

    /**
     * Returns the cached revision of the entity.
     *
     * @return the revision.
     */
    long getRevision();

    /**
     * Indicates whether this cache entry is marked as deleted.
     *
     * @return {@code true} if this cache entry is marked as deleted {@code false} else.
     */
    boolean isDeleted();

    /**
     * Returns a copy of this cache entry marked as deleted.
     *
     * @param revision revision number of the delete command. It is used as the LWWR-clock. A revision number
     * smaller than or equal to the sequence number in the cache results in a successful cache command with no effect.
     * @return the cache entry.
     */
    CacheEntry asDeleted(long revision);

    /**
     * Returns the cached JSON schema version of the entity.
     *
     * @return the JSON schema version or an empty Optional.
     */
    Optional<JsonSchemaVersion> getJsonSchemaVersion();

}
