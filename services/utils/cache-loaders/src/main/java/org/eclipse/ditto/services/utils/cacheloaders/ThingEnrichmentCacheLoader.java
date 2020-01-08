/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.utils.cache.CacheLookupContext;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads enriched Things by asking the concierge (passed in via {@code commandHandler}) so that authentication and thus
 * JSON result projection is done in user scope.
 */
@Immutable
public final class ThingEnrichmentCacheLoader
        implements AsyncCacheLoader<EntityIdWithResourceType, JsonObject> {

    private final ActorAskCacheLoader<JsonObject, Command> delegate;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param commandHandler the actor used to send "retrieve" signals.
     */
    public ThingEnrichmentCacheLoader(final Duration askTimeout, final ActorRef commandHandler) {
        final BiFunction<EntityId, CacheLookupContext, Command> commandCreator = ThingCommandFactory::retrieveThing;
        final BiFunction<Object, CacheLookupContext, Entry<JsonObject>> responseTransformer =
                ThingEnrichmentCacheLoader::handleRetrieveThingResponse;

        delegate =
                ActorAskCacheLoader.forShard(askTimeout, ThingCommand.RESOURCE_TYPE, commandHandler, commandCreator,
                        responseTransformer);
    }

    @Override
    public CompletableFuture<JsonObject> asyncLoad(final EntityIdWithResourceType key, final Executor executor) {
        return delegate.asyncLoad(key, executor)
                .thenApply(foo -> {
                    if (foo.exists()) {
                        return foo.getValueOrThrow();
                    } else {
                        throw ThingNotAccessibleException.newBuilder(ThingId.of(key.getId()))
                                .dittoHeaders(key.getCacheLookupContext()
                                        .flatMap(CacheLookupContext::getDittoHeaders)
                                        .orElseGet(DittoHeaders::empty)
                                )
                                .build();
                    }
                });
    }

    private static Entry<JsonObject> handleRetrieveThingResponse(final Object response,
            @Nullable final CacheLookupContext cacheLookupContext) {
        if (response instanceof RetrieveThingResponse) {
            final RetrieveThingResponse retrieveThingResponse = (RetrieveThingResponse) response;
            final Thing thing = retrieveThingResponse.getThing();
            final long revision = thing.getRevision().map(ThingRevision::toLong)
                    .orElseThrow(badThingResponse("no revision"));
            final JsonObject jsonObject = Optional.ofNullable(cacheLookupContext)
                    .flatMap(CacheLookupContext::getJsonFieldSelector)
                    .map(thing::toJson)
                    .orElseGet(thing::toJson);
            return Entry.of(revision, jsonObject);
        } else if (response instanceof ThingNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect RetrieveThingResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad RetrieveThingResponse: " + message);
    }

}
