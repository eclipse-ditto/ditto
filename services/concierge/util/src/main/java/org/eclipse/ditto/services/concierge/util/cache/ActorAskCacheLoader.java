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
package org.eclipse.ditto.services.concierge.util.cache;

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

import org.eclipse.ditto.services.concierge.util.EntityRegionMap;
import org.eclipse.ditto.services.concierge.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.concierge.EntityId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.pattern.PatternsCS;

/**
 * Asynchronous cache loader that loads a value by asking an actor provided by a "Entity-Region-Provider".
 *
 * @param <V> type of values in the cache entry.
 */
@Immutable
public final class ActorAskCacheLoader<V> implements AsyncCacheLoader<EntityId, Entry<V>> {

    private final long askTimeoutMillis;
    private final Function<String, ActorRef> entityRegionProvider;
    private final Map<String, Function<String, Object>> commandMap;
    private final Map<String, Function<Object, Entry<V>>> transformerMap;

    /**
     * Constructor for creating an {@link ActorAskCacheLoader} which supports multiple resource types.
     *
     * @param askTimeout the ask timeout.
     * @param entityRegionProvider function providing an entity region for a resource type.
     * @param commandMap functions per resource type for creating a load-command by an entity id (without resource
     * type).
     * @param transformerMap functions per resource type for mapping a load-response to an {@link Entry}.
     */
    public ActorAskCacheLoader(final Duration askTimeout,
            final Function<String, ActorRef> entityRegionProvider,
            final Map<String, Function<String, Object>> commandMap,
            final Map<String, Function<Object, Entry<V>>> transformerMap) {
        this.askTimeoutMillis = requireNonNull(askTimeout).toMillis();
        this.entityRegionProvider = requireNonNull(entityRegionProvider);
        this.commandMap = Collections.unmodifiableMap(new HashMap<>(requireNonNull(commandMap)));
        this.transformerMap = Collections.unmodifiableMap(new HashMap<>(requireNonNull(transformerMap)));
    }

    /**
     * Constructor for creating an {@link ActorAskCacheLoader} which supports a single resource type.
     *
     * @param askTimeout the ask timeout.
     * @param resourceType the resource type.
     * @param entityRegion the entity region.
     * @param command function for creating a load-command by an entity id (without resource type).
     * @param transformer function for mapping a load-response to an {@link Entry}.
     */
    public ActorAskCacheLoader(final Duration askTimeout,
            final String resourceType, final ActorRef entityRegion,
            final Function<String, Object> command,
            final Function<Object, Entry<V>> transformer) {
        this.askTimeoutMillis = requireNonNull(askTimeout).toMillis();
        requireNonNull(resourceType);
        requireNonNull(entityRegion);
        this.entityRegionProvider = EntityRegionMap.singleton(resourceType, entityRegion);
        requireNonNull(command);
        this.commandMap = Collections.singletonMap(resourceType, command);
        requireNonNull(transformer);
        this.transformerMap = Collections.singletonMap(resourceType, transformer);
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
        final Function<String, Object> commandCreator = commandMap.get(resourceType);
        if (commandCreator == null) {
            final String message =
                    String.format("Don't how to create retrieve command for resource type <%s> and id <%s>",
                            resourceType, id);
            throw new NullPointerException(message);
        } else {
            return commandCreator.apply(id);
        }
    }

    private Entry<V> transformResponse(final String resourceType, final Object response) {
        return checkNotNull(transformerMap.get(resourceType), resourceType).apply(response);
    }

}
