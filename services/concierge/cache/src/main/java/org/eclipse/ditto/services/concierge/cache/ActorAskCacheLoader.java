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
package org.eclipse.ditto.services.concierge.cache;

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

import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.signals.commands.base.Command;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.pattern.PatternsCS;

/**
 * Asynchronous cache loader that loads a value by asking an actor provided by a "Entity-Region-Provider".
 *
 * @param <V> type of values in the cache entry.
 * @param <T> type of messages sent when loading entries by entity id.
 */
@Immutable
public final class ActorAskCacheLoader<V, T> implements AsyncCacheLoader<EntityId, Entry<V>> {

    private final long askTimeoutMillis;
    private final Function<String, ActorRef> entityRegionProvider;
    private final Map<String, Function<String, T>> commandCreatorMap;
    private final Map<String, Function<Object, Entry<V>>> responseTransformerMap;


    private ActorAskCacheLoader(final Duration askTimeout,
            final Function<String, ActorRef> entityRegionProvider,
            final Map<String, Function<String, T>> commandCreatorMap,
            final Map<String, Function<Object, Entry<V>>> responseTransformerMap) {
        this.askTimeoutMillis = requireNonNull(askTimeout).toMillis();
        this.entityRegionProvider = requireNonNull(entityRegionProvider);
        this.commandCreatorMap = Collections.unmodifiableMap(new HashMap<>(requireNonNull(commandCreatorMap)));
        this.responseTransformerMap =
                Collections.unmodifiableMap(new HashMap<>(requireNonNull(responseTransformerMap)));
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with a sharded entity region which supports multiple resource types.
     *
     * @param askTimeout the ask timeout.
     * @param entityRegionProvider function providing an entity region for a resource type.
     * @param commandCreatorMap functions per resource type for creating a load-command by an entity id (without resource
     * type).
     * @param responseTransformerMap functions per resource type for mapping a load-response to an {@link Entry}.
     */
    public static <V> ActorAskCacheLoader<V, Command> forShard(final Duration askTimeout,
            final Function<String, ActorRef> entityRegionProvider,
            final Map<String, Function<String, Command>> commandCreatorMap,
            final Map<String, Function<Object, Entry<V>>> responseTransformerMap) {
        return new ActorAskCacheLoader<>(askTimeout, entityRegionProvider, commandCreatorMap, responseTransformerMap);
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with a sharded entity region which supports a single resource type.
     *
     * @param askTimeout the ask timeout.
     * @param resourceType the resource type.
     * @param entityRegion the entity region.
     * @param commandCreator function for creating a load-command by an entity id (without resource type).
     * @param responseTransformer function for mapping a load-response to an {@link Entry}.
     */
    public static <V> ActorAskCacheLoader<V, Command> forShard(final Duration askTimeout,
            final String resourceType,
            final ActorRef entityRegion,
            final Function<String, Command> commandCreator,
            final Function<Object, Entry<V>> responseTransformer) {
        requireNonNull(askTimeout);
        requireNonNull(resourceType);
        requireNonNull(entityRegion);
        requireNonNull(commandCreator);
        requireNonNull(responseTransformer);
        return forShard(askTimeout,
                EntityRegionMap.singleton(resourceType, entityRegion),
                Collections.singletonMap(resourceType, commandCreator),
                Collections.singletonMap(resourceType, responseTransformer));
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with PubSub which supports multiple resource types.
     *
     * @param askTimeout the ask timeout.
     * @param pubSubMediator the PubSub mediator.
     * @param commandCreatorMap functions per resource type for creating a load-command by an entity id (without resource
     * type).
     * @param responseTransformerMap functions per resource type for mapping a load-response to an {@link Entry}.
     */
    public static <V> ActorAskCacheLoader<V, DistributedPubSubMediator.Send> forPubSub(final Duration askTimeout,
            final ActorRef pubSubMediator,
            final Map<String, Function<String, DistributedPubSubMediator.Send>> commandCreatorMap,
            final Map<String, Function<Object, Entry<V>>> responseTransformerMap) {
        return new ActorAskCacheLoader<>(askTimeout, (unused) -> pubSubMediator, commandCreatorMap,
                responseTransformerMap);
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with PubSub which supports a single resource type.
     *
     * @param askTimeout the ask timeout.
     * @param resourceType the resource type.
     * @param pubSubMediator the PubSub mediator.
     * @param commandCreator function for creating a load-command by an entity id (without resource type).
     * @param responseTransformer function for mapping a load-response to an {@link Entry}.
     */
    public static <V> ActorAskCacheLoader<V, DistributedPubSubMediator.Send> forPubSub(final Duration askTimeout,
            final String resourceType,
            final ActorRef pubSubMediator,
            final Function<String, DistributedPubSubMediator.Send> commandCreator,
            final Function<Object, Entry<V>> responseTransformer) {
        requireNonNull(askTimeout);
        requireNonNull(resourceType);
        requireNonNull(pubSubMediator);
        requireNonNull(commandCreator);
        requireNonNull(responseTransformer);
        return forPubSub(askTimeout,
                pubSubMediator,
                Collections.singletonMap(resourceType, commandCreator),
                Collections.singletonMap(resourceType, responseTransformer));
    }

    @Override
    public final CompletableFuture<Entry<V>> asyncLoad(final EntityId key, final Executor executor) {
        final String resourceType = key.getResourceType();
        return CompletableFuture.supplyAsync(() -> {
            final String entityId = getEntityId(key);
            return getCommand(resourceType, entityId);
        }, executor).thenComposeAsync(command -> {
            final ActorRef entityRegion = getEntityRegion(key.getResourceType());
            return PatternsCS.ask(entityRegion, command, askTimeoutMillis)
                    .thenApply(response -> transformResponse(resourceType, response))
                    .toCompletableFuture();
        }, executor);
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

    private T getCommand(final String resourceType, final String id) {
        final Function<String, T> commandCreator = commandCreatorMap.get(resourceType);
        if (commandCreator == null) {
            final String message =
                    String.format("Don't know how to create retrieve command for resource type <%s> and id <%s>",
                            resourceType, id);
            throw new NullPointerException(message);
        } else {
            return commandCreator.apply(id);
        }
    }

    private Entry<V> transformResponse(final String resourceType, final Object response) {
        return checkNotNull(responseTransformerMap.get(resourceType), resourceType).apply(response);
    }

}
