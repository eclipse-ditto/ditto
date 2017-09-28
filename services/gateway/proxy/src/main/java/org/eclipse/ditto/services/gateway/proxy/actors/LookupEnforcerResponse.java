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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

import akka.actor.ActorRef;

/**
 * The response message for a {@link LookupEnforcer} message.
 */
public final class LookupEnforcerResponse {

    @Nullable private final ActorRef enforcerRef;
    @Nullable private final String shardId;
    private final LookupContext<?> context;
    @Nullable private final CacheEntry cacheEntry;
    @Nullable private final Throwable error;

    public LookupEnforcerResponse(@Nullable final ActorRef enforcerRef,
            @Nullable final String shardId,
            final LookupContext<?> context,
            @Nullable final CacheEntry cacheEntry) {

        this(enforcerRef, shardId, context, cacheEntry, null);
    }

    public LookupEnforcerResponse(@Nullable final ActorRef enforcerRef,
            @Nullable final String shardId,
            final LookupContext<?> context,
            @Nullable final CacheEntry cacheEntry,
            @Nullable final Throwable error) {

        this.enforcerRef = enforcerRef;
        this.shardId = shardId;
        this.context = checkNotNull(context, "lookup context");
        this.cacheEntry = cacheEntry;
        this.error = error;
    }

    /**
     * Get the {@code ActorRef} of the shard region responsible for authorization enforcement of the command.
     *
     * @return Reference of the shard region.
     */
    public Optional<ActorRef> getEnforcerRef() {
        return Optional.ofNullable(enforcerRef);
    }

    /**
     * Get the ID of the actor within the shard region responsible for authorization enforcement of the command.
     *
     * @return ID of the enforcer actor within the shard region.
     */
    public Optional<String> getShardId() {
        return Optional.ofNullable(shardId);
    }

    public LookupContext<?> getContext() {
        return context;
    }

    public Optional<CacheEntry> getCacheEntry() {
        return Optional.ofNullable(cacheEntry);
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    @SuppressWarnings("squid:S1067")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LookupEnforcerResponse that = (LookupEnforcerResponse) o;
        return Objects.equals(enforcerRef, that.enforcerRef) &&
                Objects.equals(shardId, that.shardId) &&
                Objects.equals(context, that.context) &&
                Objects.equals(cacheEntry, that.cacheEntry) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcerRef, shardId, context, cacheEntry, error);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcerRef=" + enforcerRef +
                ", shardId=" + shardId +
                ", context=" + context +
                ", cacheEntry=" + cacheEntry +
                ", error=" + error +
                "]";
    }

}
