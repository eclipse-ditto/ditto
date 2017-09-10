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
package org.eclipse.ditto.services.models.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

/**
 * Data structure used in cluster (via Akka Distributed Data) as cache for important Policy information.
 * Don't put too much information in here as this uses memory in each cluster node and is synchronized in the cluster
 * all the time.
 */
@Immutable
public final class PolicyCacheEntry implements CacheEntry {

    private final CacheEntry baseCacheEntry;

    private PolicyCacheEntry(final CacheEntry theBaseCacheEntry) {
        baseCacheEntry = theBaseCacheEntry;
    }

    /**
     * Returns a new non deleted {@code PolicyCacheEntry} instance with the specified parameters.
     *
     * @param jsonSchemaVersion the JSON schema version of the cached thing.
     * @param revision the revision the cached Thing.
     * @return the PolicyCacheEntry instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyCacheEntry of(final JsonSchemaVersion jsonSchemaVersion, final Long revision) {
        checkNotNull(jsonSchemaVersion, "JSON schema version");
        checkNotNull(revision, "revision");

        return new PolicyCacheEntry(BaseCacheEntry.newInstance(null, revision, false, jsonSchemaVersion));
    }

    /**
     * Creates a new {@code PolicyCacheEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new PolicyCacheEntry is to be created.
     * @return the PolicyCacheEntry which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain fields for
     * <ul>
     *     <li>{@link BaseCacheEntry.JsonFields#REVISION},</li>
     *     <li>{@link BaseCacheEntry.JsonFields#DELETED} or</li>
     *     <li>{@link BaseCacheEntry.JsonFields#JSON_SCHEMA_VERSION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     */
    public static PolicyCacheEntry fromJson(final JsonObject jsonObject) {
        return new PolicyCacheEntry(BaseCacheEntry.fromJson(jsonObject));
    }

    @Override
    public Optional<JsonSchemaVersion> getJsonSchemaVersion() {
        return baseCacheEntry.getJsonSchemaVersion();
    }

    @Override
    public boolean isDeleted() {
        return baseCacheEntry.isDeleted();
    }

    @Override
    public CacheEntry asDeleted(final long revision) {
        return new PolicyCacheEntry(baseCacheEntry.asDeleted(revision));
    }

    @Override
    public long getRevision() {
        return baseCacheEntry.getRevision();
    }

    @Override
    public Optional<String> getPolicyId() {
        return baseCacheEntry.getPolicyId();
    }

    @Override
    public JsonObject toJson() {
        return baseCacheEntry.toJson();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PolicyCacheEntry that = (PolicyCacheEntry) o;
        return Objects.equals(baseCacheEntry, that.baseCacheEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCacheEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + baseCacheEntry + "]";
    }

}
