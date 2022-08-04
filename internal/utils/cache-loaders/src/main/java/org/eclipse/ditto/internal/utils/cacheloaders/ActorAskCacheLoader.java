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
package org.eclipse.ditto.internal.utils.cacheloaders;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Asynchronous cache loader that loads a value by asking an actor provided by a "Entity-Region-Provider".
 *
 * @param <V> type of values in the cache entry.
 * @param <T> type of messages sent when loading entries by entity id.
 */
@Immutable
public final class ActorAskCacheLoader<V, T, K extends EntityId>
        implements AsyncCacheLoader<K, Entry<V>> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(ActorAskCacheLoader.class);

    private final AskWithRetryConfig askWithRetryConfig;
    private final Scheduler scheduler;
    private final Function<EntityType, ActorRef> entityRegionProvider;
    private final Map<EntityType, Function<K, T>> commandCreatorMap;
    private final Map<EntityType, Function<Object, Entry<V>>> responseTransformerMap;

    private ActorAskCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final Function<EntityType, ActorRef> entityRegionProvider,
            final Map<EntityType, Function<K, T>> commandCreatorMap,
            final Map<EntityType, Function<Object, Entry<V>>> responseTransformerMap) {
        this.askWithRetryConfig = requireNonNull(askWithRetryConfig);
        this.scheduler = scheduler;
        this.entityRegionProvider = requireNonNull(entityRegionProvider);
        this.commandCreatorMap = Map.copyOf(requireNonNull(commandCreatorMap));
        this.responseTransformerMap = Map.copyOf(requireNonNull(responseTransformerMap));
    }

    /**
     * Constructs an {@link ActorAskCacheLoader} with a sharded entity region which supports a single resource type.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param entityType the entity type.
     * @param entityRegion the entity region.
     * @param commandCreator function for creating a load-command by an entity id (without resource type).
     * @param responseTransformer function for mapping a load-response to an {@link Entry}.
     * @param <V> type of values in the cache entry.
     * @return the built ActorAskCacheLoader.
     */
    public static <V, K extends EntityId> ActorAskCacheLoader<V, Command<?>, K> forShard(
            final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final EntityType entityType,
            final ActorRef entityRegion,
            final Function<K, Command<?>> commandCreator,
            final Function<Object, Entry<V>> responseTransformer) {

        requireNonNull(askWithRetryConfig);
        requireNonNull(entityType);
        requireNonNull(entityRegion);
        requireNonNull(commandCreator);
        requireNonNull(responseTransformer);
        return new ActorAskCacheLoader<>(askWithRetryConfig, scheduler,
                EntityRegionMap.singleton(entityType, entityRegion),
                Collections.singletonMap(entityType, commandCreator),
                Collections.singletonMap(entityType, responseTransformer));
    }

    @Override
    public CompletableFuture<Entry<V>> asyncLoad(final K entityId, final Executor executor) {
        final var entityType = entityId.getEntityType();
        return CompletableFuture.supplyAsync(() -> getCommand(entityType, entityId), executor)
                .thenCompose(command -> {
                    final ActorRef entityRegion = getEntityRegion(entityType);
                    LOGGER.debug("Going to retrieve cache entry for key <{}> with command <{}>: ", entityId, command);

                    return AskWithRetry.askWithRetry(entityRegion, command, askWithRetryConfig, scheduler, executor,
                            response -> transformResponse(entityType, response)
                    );
                });
    }

    private ActorRef getEntityRegion(final EntityType entityType) {
        final ActorRef entityRegion = entityRegionProvider.apply(entityType);
        if (entityRegion == null) {
            throw new IllegalStateException("null entity region returned for resource type " + entityType);
        }

        return entityRegion;
    }

    private T getCommand(final EntityType entityType, final K id) {
        final Function<K, T> commandCreator = commandCreatorMap.get(entityType);
        if (commandCreator == null) {
            final var message =
                    String.format("Don't know how to create retrieve command for resource type <%s> and id <%s>",
                            entityType, id);
            throw new NullPointerException(message);
        } else {
            return commandCreator.apply(id);
        }
    }

    private Entry<V> transformResponse(final EntityType entityType, final Object response) {
        return checkNotNull(responseTransformerMap.get(entityType), "entityType").apply(response);
    }

}
