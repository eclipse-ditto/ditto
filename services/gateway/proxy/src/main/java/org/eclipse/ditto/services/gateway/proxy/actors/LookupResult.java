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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

import akka.actor.ActorRef;

/**
 * Provides the Shard ID, {@link CacheEntry} and {@link ActorRef} of an entity in the cluster.
 */
public final class LookupResult {

    @Nullable private final String shardId;
    @Nullable private final CacheEntry cacheEntry;
    @Nullable private final ActorRef actorRef;
    @Nullable private final Throwable error;

    private LookupResult(@Nullable final String shardId,
            @Nullable final CacheEntry cacheEntry,
            @Nullable  final ActorRef actorRef,
            @Nullable final Throwable error) {

        this.shardId = shardId;
        this.cacheEntry = cacheEntry;
        this.actorRef = actorRef;
        this.error = error;
    }

    /**
     * Returns a new {@code LookupResult} for the given {@code shardId}, {@code cacheEntry} and {@code actorRef}.
     *
     * @param shardId the ID of the shard in the sharding region.
     * @param cacheEntry the cache entry for the entity.
     * @param actorRef the actor ref of the entity.
     * @return the LookupResult.
     */
    public static LookupResult of(@Nullable final String shardId, @Nullable final CacheEntry cacheEntry,
            @Nullable final ActorRef actorRef) {

        return new LookupResult(shardId, cacheEntry, actorRef, null);
    }

    /**
     * Returns a new {@code LookupResult} for an entity not found in the cluster.
     *
     * @return the LookupResult.
     */
    public static LookupResult notFound() {
        return new LookupResult(null, null, null, null);
    }

    /**
     * Returns a new {@code LookupResult} for a lookup which returned a {@link Throwable}.
     *
     * @param error the Error which occurred during the lookup.
     * @return the LookupResult.
     */
    public static LookupResult withError(final Throwable error) {
        return new LookupResult(null, null, null, error);
    }

    /**
     * Returns the Shard ID of the entity.
     *
     * @return the Shard ID.
     */
    public Optional<String> getShardId() {
        return Optional.ofNullable(shardId);
    }

    /**
     * Returns the {@code CacheEntry} of the entity.
     *
     * @return the CacheEntry.
     */
    public Optional<CacheEntry> getCacheEntry() {
        return Optional.ofNullable(cacheEntry);
    }

    /**
     * Returns the {@code ActorRef} of the entity.
     *
     * @return the ActorRef.
     */
    public Optional<ActorRef> getActorRef() {
        return Optional.ofNullable(actorRef);
    }

    /**
     * Returns the {@code Throwable} of a potential error during the lookup of the entity.
     *
     * @return the Error.
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LookupResult that = (LookupResult) o;
        return Objects.equals(shardId, that.shardId) &&
                Objects.equals(cacheEntry, that.cacheEntry) &&
                Objects.equals(actorRef, that.actorRef) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardId, cacheEntry, actorRef, error);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "shardId=" + shardId +
                ", cacheEntry=" + cacheEntry +
                ", actorRef=" + actorRef +
                ", error=" + error +
                "]";
    }

}
