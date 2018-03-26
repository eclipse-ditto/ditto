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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.pattern.PatternsCS;

/**
 * Asynchronous cache loader that loads a value by asking an actor provided by a "Entity-Region-Provider".
 *
 * @param <V> type of values in the cache entry.
 */
@Immutable
final class ActorAskCacheLoader<V> implements AsyncCacheLoader<EntityId, Entry<V>> {

    private final long askTimeoutMillis;
    final Function<String, ActorRef> entityRegionProvider;
    private final Map<String, Function<String, Object>> commandMap;
    private final Map<String, Function<Object, Entry<V>>> transformerMap;

    protected ActorAskCacheLoader(final Duration askTimeout,
            final Function<String, ActorRef> entityRegionProvider,
            final Map<String, Function<String, Object>> commandMap,
            final Map<String, Function<Object, Entry<V>>> transformerMap) {
        this.askTimeoutMillis = requireNonNull(askTimeout).toMillis();
        this.entityRegionProvider = requireNonNull(entityRegionProvider);
        this.commandMap = Collections.unmodifiableMap(new HashMap<>(requireNonNull(commandMap)));
        this.transformerMap = Collections.unmodifiableMap(new HashMap<>(requireNonNull(transformerMap)));
    }

    @Override
    public final CompletableFuture<Entry<V>> asyncLoad(final EntityId key, final Executor executor) {
        final String resourceType = key.getResourceType();
        return CompletableFuture.supplyAsync(() -> {
            final String entityId = getEntityId(key);
            return getCommand(resourceType, entityId);
        }).thenCompose(command -> {
            final ActorRef entityRegion = getEntityRegion(key.getResourceType());
            return PatternsCS.ask(entityRegion, command, askTimeoutMillis)
                    .thenApply(response -> transformResponse(resourceType, response))
                    .toCompletableFuture();
        });
    }

    private static String getEntityId(final EntityId key) {
        return key.getId();
    }

    private ActorRef getEntityRegion(final String resourceType) {
        final ActorRef entityRegion = entityRegionProvider.apply(resourceType);
        if (entityRegion == null) {
            throw new IllegalStateException("null entity region returned for resource type " +
                    resourceType);
        }

        return entityRegion;
    }

    private Object getCommand(final String resourceType, final String id) {
        return checkNotNull(commandMap.get(resourceType), resourceType).apply(id);
    }

    private Entry<V> transformResponse(final String resourceType, final Object response) {
        return checkNotNull(transformerMap.get(resourceType), resourceType).apply(response);
    }

}
