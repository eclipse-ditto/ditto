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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

/**
 * Retrieve additional parts of things by asking an asynchronous cache.
 * Instantiated once per cluster node so that it builds up a cache across all signal enrichments on a local cluster
 * node.
 */
public final class CachingSignalEnrichmentFacade implements
        SignalEnrichmentFacade {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory
            .getThreadSafeLogger(CachingSignalEnrichmentFacade.class);

    private final Cache<SignalEnrichmentCacheKey, JsonObject> extraFieldsCache;

    private CachingSignalEnrichmentFacade(
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        extraFieldsCache = CacheFactory.createCache(
                SignalEnrichmentCacheLoader.of(cacheLoaderFacade),
                cacheConfig,
                cacheNamePrefix + "_signal_enrichment_cache",
                cacheLoaderExecutor);
    }

    /**
     * Create a signal-enriching facade that retrieves partial things by using a Caffeine cache.
     *
     * @param cacheLoaderFacade the facade whose argument-result-pairs we are caching.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @param cacheNamePrefix the prefix to use as cacheName of the cache.
     * @return The facade.
     * @throws NullPointerException if any argument is null.
     */
    public static CachingSignalEnrichmentFacade of(final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig, final Executor cacheLoaderExecutor, final String cacheNamePrefix) {

        return new CachingSignalEnrichmentFacade(cacheLoaderFacade, cacheConfig, cacheLoaderExecutor,
                cacheNamePrefix);
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            @Nullable final Signal<?> concernedSignal) {

        // as second step only return what was originally requested as fields:
        final List<Signal<?>> concernedSignals = concernedSignal == null ? List.of() : List.of(concernedSignal);
        return doRetrievePartialThing(thingId, jsonFieldSelector, dittoHeaders, concernedSignals, true, 0)
                .thenApply(jsonObject -> jsonObject.get(jsonFieldSelector));
    }

    /**
     * Retrieve thing given a list of thing events.
     *
     * @param thingId the thing to retrieve.
     * @param events received thing events to reduce traffic. If there are no events, a fresh entry is retrieved.
     * @param minAcceptableSeqNr the minimum sequence number acceptable as result. If negative,
     * cache loading is forced.
     * @return future of the retrieved thing.
     */
    public CompletionStage<JsonObject> retrieveThing(final ThingId thingId, final List<ThingEvent<?>> events,
            final long minAcceptableSeqNr) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        if (minAcceptableSeqNr < 0) {
            final var cacheKey =
                    SignalEnrichmentCacheKey.of(thingId, SignalEnrichmentContext.of(dittoHeaders, null));
            extraFieldsCache.invalidate(cacheKey);
            return doCacheLookup(cacheKey, dittoHeaders);
        } else {
            return doRetrievePartialThing(thingId, null, dittoHeaders, events, false, minAcceptableSeqNr);
        }
    }

    private CompletionStage<JsonObject> doRetrievePartialThing(final ThingId thingId,
            @Nullable final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            final List<? extends Signal<?>> concernedSignals,
            final boolean invalidateCacheOnPolicyChange,
            final long minAcceptableSeqNr) {

        final JsonFieldSelector enhancedFieldSelector;
        if (jsonFieldSelector == null) {
            enhancedFieldSelector = null;
        } else {
            enhancedFieldSelector = JsonFactory.newFieldSelectorBuilder()
                    .addPointers(jsonFieldSelector)
                    .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                    .build();
        }

        final var cacheKey = SignalEnrichmentCacheKey.of(thingId,
                SignalEnrichmentContext.of(dittoHeaders, enhancedFieldSelector));

        return smartUpdateCachedObject(enhancedFieldSelector, cacheKey, concernedSignals,
                invalidateCacheOnPolicyChange, minAcceptableSeqNr);
    }

    private Optional<Integer> findLastThingDeletedOrCreated(final List<ThingEvent<?>> thingEvents) {
        for (int i = thingEvents.size() - 1; i >= 0; --i) {
            final var event = thingEvents.get(i);
            if (event instanceof ThingDeleted || event instanceof ThingCreated) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private Optional<List<ThingEvent<?>>> extractConsecutiveTwinEvents(
            final List<? extends Signal<?>> concernedSignals, final long minAcceptableSeqNr) {
        final List<ThingEvent<?>> thingEvents = concernedSignals.stream()
                .filter(signal -> (signal instanceof ThingEvent) && !(ProtocolAdapter.isLiveSignal(signal)))
                .map(signal -> (ThingEvent<?>) signal)
                .collect(Collectors.toList());

        // ignore events before ThingDeleted or ThingCreated
        // not safe to ignore events before ThingModified because ThingModified has merge semantics at the top level
        final var events = findLastThingDeletedOrCreated(thingEvents)
                .map(i -> thingEvents.subList(i, thingEvents.size()))
                .orElse(thingEvents);

        // Check if minimum acceptable sequence number is met
        if (minAcceptableSeqNr >= 0 && (events.isEmpty() || getLast(events).getRevision() < minAcceptableSeqNr)) {
            return Optional.empty();
        }

        // Validate sequence numbers. Discard if events have gaps
        if (!events.isEmpty()) {
            long lastSeq = -1;
            for (final ThingEvent<?> event : events) {
                if (lastSeq >= 0 && event.getRevision() != lastSeq + 1) {
                    return Optional.empty();
                } else {
                    lastSeq = event.getRevision();
                }
            }
        }

        return Optional.of(events);
    }

    private CompletableFuture<JsonObject> doCacheLookup(final SignalEnrichmentCacheKey cacheKey,
            final DittoHeaders dittoHeaders) {

        LOGGER.withCorrelationId(dittoHeaders)
                .debug("Looking up cache entry for <{}>", cacheKey);
        return extraFieldsCache.get(cacheKey)
                .thenApply(optionalJsonObject -> optionalJsonObject.orElseGet(JsonObject::empty));
    }

    private CompletableFuture<JsonObject> smartUpdateCachedObject(
            @Nullable final JsonFieldSelector enhancedFieldSelector,
            final SignalEnrichmentCacheKey cacheKey,
            final List<? extends Signal<?>> concernedSignals,
            final boolean invalidateCacheOnPolicyChange,
            final long minAcceptableSeqNr) {

        final Optional<List<ThingEvent<?>>> thingEventsOptional =
                extractConsecutiveTwinEvents(concernedSignals, minAcceptableSeqNr);
        final var dittoHeaders = getLastDittoHeaders(concernedSignals);

        // there are twin events, but their sequence numbers have gaps or do not reach the min acceptable seq nr
        if (thingEventsOptional.isEmpty()) {
            extraFieldsCache.invalidate(cacheKey);
            return doCacheLookup(cacheKey, dittoHeaders);
        }

        final var thingEvents = thingEventsOptional.orElseThrow();

        // there are no twin event; return the cached thing
        if (thingEvents.isEmpty()) {
            return doCacheLookup(cacheKey, dittoHeaders);
        }

        // there are ThingCreated events: perform smart update ignoring the cache entry
        if (thingEvents.get(0) instanceof ThingCreated) {
            return handleNextExpectedThingEvents(enhancedFieldSelector, cacheKey, thingEvents,
                    JsonObject.empty(), invalidateCacheOnPolicyChange)
                    .toCompletableFuture();
        }

        // there are twin events; perform smart update
        return doCacheLookup(cacheKey, dittoHeaders).thenCompose(cachedJsonObject -> {
            final long cachedRevision = cachedJsonObject.getValue(Thing.JsonFields.REVISION).orElse(0L);
            final var relevantEvents = thingEvents.stream()
                    .filter(e -> e.getRevision() > cachedRevision)
                    .collect(Collectors.toList());
            if (relevantEvents.isEmpty()) {
                // the cache entry was more up-to-date
                return CompletableFuture.completedFuture(cachedJsonObject);
            } else if (cachedRevision + 1 == getFirst(relevantEvents).getRevision()) {
                // the cache entry was already present and the first thingEvent was the next expected revision no
                // -> we have all information necessary to calculate it without making another roundtrip
                return handleNextExpectedThingEvents(enhancedFieldSelector, cacheKey, relevantEvents,
                        cachedJsonObject, invalidateCacheOnPolicyChange);
            } else {
                // the cache entry was already present, but we missed sth and need to invalidate the cache
                // and to another cache lookup (via roundtrip)
                extraFieldsCache.invalidate(cacheKey);
                return doCacheLookup(cacheKey, dittoHeaders);
            }
        });
    }

    private DittoHeaders getLastDittoHeaders(final List<? extends Signal<?>> concernedSignals) {
        if (concernedSignals.isEmpty()) {
            return DittoHeaders.empty();
        } else {
            return getLast(concernedSignals).getDittoHeaders();
        }
    }

    private <T> T getLast(final List<T> list) {
        return list.get(list.size() - 1);
    }

    private <T> T getFirst(final List<T> list) {
        return list.get(0);
    }

    private CompletionStage<JsonObject> handleNextExpectedThingEvents(
            @Nullable final JsonFieldSelector enhancedFieldSelector,
            final SignalEnrichmentCacheKey cacheKey,
            final List<ThingEvent<?>> thingEvents,
            final JsonObject cachedJsonObject,
            final boolean invalidateCacheOnPolicyChange) {

        final Optional<String> cachedPolicyIdOpt = cachedJsonObject.getValue(Thing.JsonFields.POLICY_ID);
        JsonObject jsonObject = cachedJsonObject;
        for (final ThingEvent<?> thingEvent : thingEvents) {
            final JsonPointer resourcePath = thingEvent.getResourcePath();

            switch (thingEvent.getCommandCategory()) {
                case MERGE:
                    final var thingMerged = (ThingMerged) thingEvent;
                    final JsonValue mergedValue = thingMerged.getValue();
                    final JsonObject mergePatch = JsonFactory.newObject(resourcePath, mergedValue);
                    jsonObject = JsonFactory.mergeJsonValues(mergePatch, jsonObject).asObject();
                    break;
                case DELETE:
                    if (thingEvent instanceof ThingDeleted) {
                        // NoOp because we just want to keep the original known thing.
                    } else if (resourcePath.isEmpty()) {
                        jsonObject = JsonObject.empty();
                    } else {
                        jsonObject = jsonObject.remove(resourcePath);
                    }
                    break;
                case MODIFY:
                default:
                    final var jsonObjectBuilder = jsonObject.toBuilder();
                    final Optional<JsonValue> optEntity = thingEvent.getEntity();
                    if (resourcePath.isEmpty() && optEntity.filter(JsonValue::isObject).isPresent()) {
                        optEntity.map(JsonValue::asObject).ifPresent(jsonObjectBuilder::setAll);
                    } else {
                        optEntity.ifPresent(entity -> jsonObjectBuilder
                                .set(resourcePath.toString(), entity)
                        );
                    }
                    jsonObject = jsonObjectBuilder.build();
            }

            // invalidate cache on policy change if the flag is set
            if (invalidateCacheOnPolicyChange) {
                final var currentJsonObject = jsonObject;
                final boolean shouldInvalidate = cachedPolicyIdOpt.flatMap(cachedPolicyId ->
                                currentJsonObject.getValue(Thing.JsonFields.POLICY_ID)
                                        .filter(currentPolicyId -> !cachedPolicyId.equals(currentPolicyId)))
                        .isPresent();
                if (shouldInvalidate) {
                    // invalidate the cache
                    extraFieldsCache.invalidate(cacheKey);
                    // and to another cache lookup (via roundtrip):
                    return doCacheLookup(cacheKey, thingEvent.getDittoHeaders());
                }
            }
        }
        final var jsonObjectBuilder = jsonObject.toBuilder()
                .set(Thing.JsonFields.REVISION, getLast(thingEvents).getRevision());
        final var enhancedJsonObject = enhancedFieldSelector == null
                ? jsonObjectBuilder.build()
                : jsonObjectBuilder.build().get(enhancedFieldSelector);
        // update local cache with enhanced object:
        extraFieldsCache.put(cacheKey, enhancedJsonObject);
        return CompletableFuture.completedFuture(enhancedJsonObject);
    }

}
