/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cacheloaders;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.services.utils.cache.CacheLookupContext;
import org.eclipse.ditto.services.utils.cache.CacheKey;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.signals.commands.base.Command;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.pattern.Patterns;

/**
 * Asynchronous cache loader that loads a value by asking an actor provided by a "Entity-Region-Provider".
 *
 * @param <V> type of values in the cache entry.
 * @param <T> type of messages sent when loading entries by entity id.
 */
@Immutable
public final class ActorAskCacheLoader<V, T> implements AsyncCacheLoader<CacheKey, Entry<V>> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(ActorAskCacheLoader.class);

    private final Duration askTimeout;
    private final Function<EntityType, ActorRef> entityRegionProvider;
    private final Map<EntityType, BiFunction<EntityId, CacheLookupContext, T>> commandCreatorMap;
    private final Map<EntityType, BiFunction<Object, CacheLookupContext, Entry<V>>> responseTransformerMap;

    private ActorAskCacheLoader(final Duration askTimeout,
            final Function<EntityType, ActorRef> entityRegionProvider,
            final Map<EntityType, BiFunction<EntityId, CacheLookupContext, T>> commandCreatorMap,
            final Map<EntityType, BiFunction<Object, CacheLookupContext, Entry<V>>> responseTransformerMap) {
        this.askTimeout = requireNonNull(askTimeout);
        this.entityRegionProvider = requireNonNull(entityRegionProvider);
        this.commandCreatorMap = Map.copyOf(requireNonNull(commandCreatorMap));
        this.responseTransformerMap = Map.copyOf(requireNonNull(responseTransformerMap));
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
    public static <V> ActorAskCacheLoader<V, Command<?>> forShard(final Duration askTimeout,
            final Function<EntityType, ActorRef> entityRegionProvider,
            final Map<EntityType, BiFunction<EntityId, CacheLookupContext, Command<?>>> commandCreatorMap,
            final Map<EntityType, BiFunction<Object, CacheLookupContext, Entry<V>>> responseTransformerMap) {
        return new ActorAskCacheLoader<>(askTimeout, entityRegionProvider, commandCreatorMap, responseTransformerMap);
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with a sharded entity region which supports a single resource type.
     *
     * @param askTimeout the ask timeout.
     * @param entityType the entity type.
     * @param entityRegion the entity region.
     * @param commandCreator function for creating a load-command by an entity id (without resource type).
     * @param responseTransformer function for mapping a load-response to an {@link Entry}.
     */
    public static <V> ActorAskCacheLoader<V, Command<?>> forShard(final Duration askTimeout,
            final EntityType entityType,
            final ActorRef entityRegion,
            final BiFunction<EntityId, CacheLookupContext, Command<?>> commandCreator,
            final BiFunction<Object, CacheLookupContext, Entry<V>> responseTransformer) {
        requireNonNull(askTimeout);
        requireNonNull(entityType);
        requireNonNull(entityRegion);
        requireNonNull(commandCreator);
        requireNonNull(responseTransformer);
        return forShard(askTimeout,
                EntityRegionMap.singleton(entityType, entityRegion),
                Collections.singletonMap(entityType, commandCreator),
                Collections.singletonMap(entityType, responseTransformer));
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
    public static <V> ActorAskCacheLoader<V, DistributedPubSubMediator.Send> forPubSub(
            final Duration askTimeout,
            final ActorRef pubSubMediator,
            final Map<EntityType, BiFunction<EntityId, CacheLookupContext, DistributedPubSubMediator.Send>> commandCreatorMap,
            final Map<EntityType, BiFunction<Object, CacheLookupContext, Entry<V>>> responseTransformerMap) {
        return new ActorAskCacheLoader<>(askTimeout, unused -> pubSubMediator, commandCreatorMap,
                responseTransformerMap);
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with PubSub which supports a single resource type.
     *
     * @param askTimeout the ask timeout.
     * @param entityType the resource type.
     * @param pubSubMediator the PubSub mediator.
     * @param commandCreator function for creating a load-command by an entity id (without resource type).
     * @param responseTransformer function for mapping a load-response to an {@link Entry}.
     */
    public static <V> ActorAskCacheLoader<V, DistributedPubSubMediator.Send> forPubSub(
            final Duration askTimeout,
            final EntityType entityType,
            final ActorRef pubSubMediator,
            final BiFunction<EntityId, CacheLookupContext, DistributedPubSubMediator.Send> commandCreator,
            final BiFunction<Object, CacheLookupContext, Entry<V>> responseTransformer) {
        requireNonNull(askTimeout);
        requireNonNull(entityType);
        requireNonNull(pubSubMediator);
        requireNonNull(commandCreator);
        requireNonNull(responseTransformer);
        return forPubSub(askTimeout,
                pubSubMediator,
                Collections.singletonMap(entityType, commandCreator),
                Collections.singletonMap(entityType, responseTransformer));
    }

    @Override
    public final CompletableFuture<Entry<V>> asyncLoad(final CacheKey key, final Executor executor) {
        final EntityType entityType = key.getId().getEntityType();
        return CompletableFuture.supplyAsync(() -> {
            final EntityId entityId = key.getId();
            return getCommand(entityType, entityId, key.getCacheLookupContext().orElse(null));
        }, executor).thenCompose(command -> {
            final ActorRef entityRegion = getEntityRegion(entityType);
            LOGGER.debug("Going to retrieve cache entry for key <{}> with command <{}>: ", key, command);
            return Patterns.ask(entityRegion, command, askTimeout)
                    .thenApply(response -> transformResponse(
                            entityType, response, key.getCacheLookupContext().orElse(null)))
                    .toCompletableFuture();
        });
    }

    private ActorRef getEntityRegion(final EntityType entityType) {
        final ActorRef entityRegion = entityRegionProvider.apply(entityType);
        if (entityRegion == null) {
            throw new IllegalStateException("null entity region returned for resource type " +
                    entityType);
        }

        return entityRegion;
    }

    private T getCommand(final EntityType entityType, final EntityId id,
            @Nullable final CacheLookupContext cacheLookupContext) {
        final BiFunction<EntityId, CacheLookupContext, T> commandCreator = commandCreatorMap.get(entityType);
        if (commandCreator == null) {
            final String message =
                    String.format("Don't know how to create retrieve command for resource type <%s> and id <%s>",
                            entityType, id);
            throw new NullPointerException(message);
        } else {
            return commandCreator.apply(id, cacheLookupContext);
        }
    }

    private Entry<V> transformResponse(final EntityType entityType, final Object response,
            @Nullable final CacheLookupContext cacheLookupContext) {
        return checkNotNull(responseTransformerMap.get(entityType), "entityType").apply(response, cacheLookupContext);
    }

}
