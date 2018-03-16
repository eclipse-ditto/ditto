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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.pattern.PatternsCS;

/**
 * Asynchronous cache loader that loads a value by asking an actor. Subclasses are accessed in parallel from many
 * threads. They should all be immutable.
 *
 * @param <V> type of values in the cache entry.
 */
abstract class AbstractAskCacheLoader<V> implements AsyncCacheLoader<ResourceKey, Entry<V>> {

    private final long askTimeoutMillis;
    private final EntityRegionMap entityRegionMap;
    private final Map<String, Function<String, Object>> commandMap;
    private final Map<String, Function<Object, Entry<V>>> transformerMap;

    protected AbstractAskCacheLoader(final Duration askTimeout, final EntityRegionMap entityRegionMap) {
        this.askTimeoutMillis = askTimeout.toMillis();
        this.entityRegionMap = entityRegionMap;
        this.commandMap = Collections.unmodifiableMap(new HashMap<>(buildCommandMap()));
        this.transformerMap = Collections.unmodifiableMap(new HashMap<>(buildTransformerMap()));
    }

    /**
     * Map resource type to the command used to retrieve the ID of the authorization data for the entity.
     * Subclasses may override this method to handle additional resource types a la "cake pattern".
     *
     * @return A mutable map from resource types to authorization retrieval commands.
     */
    protected HashMap<String, Function<String, Object>> buildCommandMap() {
        return new HashMap<>();
    }

    /**
     * Map resource type to the transformation applied to responses. Subclasses may override this method to handle
     * additional resource types a la "cake pattern".
     *
     * @return A mutable map containing response transformations.
     */
    protected HashMap<String, Function<Object, Entry<V>>> buildTransformerMap() {
        return new HashMap<>();
    }

    @Override
    public final CompletableFuture<Entry<V>> asyncLoad(final ResourceKey key, final Executor executor) {
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

    static SudoRetrieveThing sudoRetrieveThing(final String thingId) {
        final JsonFieldSelector jsonFieldSelector = JsonFieldSelector.newInstance(
                Thing.JsonFields.ID.getPointer(),
                Thing.JsonFields.REVISION.getPointer(),
                Thing.JsonFields.ACL.getPointer(),
                Thing.JsonFields.POLICY_ID.getPointer());
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId, jsonFieldSelector, DittoHeaders.empty());
    }

    private static String getEntityId(final ResourceKey key) {
        return key.getResourcePath().getRoot().map(JsonKey::toString).orElse("");
    }

    private ActorRef getEntityRegion(final String resourceType) {
        return entityRegionMap.lookup(resourceType)
                .orElseThrow(() -> new IllegalArgumentException("no entity region for resource type " + resourceType));
    }

    private Object getCommand(final String resourceType, final String id) {
        return checkNotNull(commandMap.get(resourceType), resourceType).apply(id);
    }

    @Nullable
    private Entry<V> transformResponse(final String resourceType, final Object response) {
        return checkNotNull(transformerMap.get(resourceType), resourceType).apply(response);
    }


    static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }

    static Supplier<RuntimeException> badPolicyResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: " + message);
    }
}
