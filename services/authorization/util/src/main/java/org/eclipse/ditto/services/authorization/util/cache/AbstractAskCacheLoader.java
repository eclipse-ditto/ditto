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
package org.eclipse.ditto.services.authorization.util.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.pattern.PatternsCS;

/**
 * Asynchronous cache loader that loads a value by asking an actor. Subclasses are accessed in parallel from many
 * threads. They should all be immutable.
 *
 * @param <V> type of values in the cache entry.
 */
@AllValuesAreNonnullByDefault
abstract class AbstractAskCacheLoader<V> implements AsyncCacheLoader<ResourceKey, Entry<V>> {

    /**
     * How long to wait for the entity region's answer.
     *
     * @return the timeout.
     */
    protected abstract Duration getAskTimeout();

    /**
     * Get the proxy actor to the shard region of entities based on resource type.
     *
     * @param resourceType resource type of said entities.
     * @return proxy actor to the shard region.
     */
    protected abstract ActorRef getEntityRegion(final String resourceType);

    /**
     * Get the command to ask the entity region with.
     *
     * @param resourceType resource type of said entities.
     * @param id id of the entity to ask about.
     * @return the command to send to the entity region.
     */
    protected abstract Object getCommand(final String resourceType, final String id);

    /**
     * Construct a cache entry from a response containing all necessary information.
     *
     * @param resourceType resource type of the response.
     * @param response response with all necessary information.
     * @return the cache entry.
     */
    protected abstract Entry<V> transformResponse(final String resourceType, final Object response);

    @Override
    public final CompletableFuture<Entry<V>> asyncLoad(final ResourceKey key, final Executor executor) {
        final String resourceType = key.getResourceType();
        return CompletableFuture.supplyAsync(() -> {
            final String entityId = getEntityId(key);
            return getCommand(resourceType, entityId);
        }).thenCompose(command -> {
            final ActorRef entityRegion = getEntityRegion(key.getResourceType());
            return PatternsCS.ask(entityRegion, command, getAskTimeout().toMillis())
                    .thenApply(response -> transformResponse(resourceType, response))
                    .toCompletableFuture();
        });
    }

    private static String getEntityId(final ResourceKey key) {
        return key.getResourcePath().getRoot().map(JsonKey::toString).orElse("");
    }

}
