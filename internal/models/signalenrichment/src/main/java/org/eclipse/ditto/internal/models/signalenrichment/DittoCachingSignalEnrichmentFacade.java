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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.CacheKey;
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
public final class DittoCachingSignalEnrichmentFacade implements CachingSignalEnrichmentFacade {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory
            .getThreadSafeLogger(DittoCachingSignalEnrichmentFacade.class);
    private static final String CACHE_NAME_SUFFIX = "_signal_enrichment_cache";

    private final Cache<CacheKey, JsonObject> extraFieldsCache;

    private DittoCachingSignalEnrichmentFacade(final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        final var cacheLoader = SignalEnrichmentCacheLoader.of(cacheLoaderFacade);
        final var cacheName = cacheNamePrefix + CACHE_NAME_SUFFIX;

        extraFieldsCache = CacheFactory.createCache(
                cacheLoader,
                cacheConfig,
                cacheName,
                cacheLoaderExecutor);
    }

    /**
     * Returns a new {@code DittoCachingSignalEnrichmentFacade} instance.
     *
     * @param cacheLoaderFacade the facade whose argument-result-pairs we are caching.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @param cacheNamePrefix the prefix to use as cacheName of the cache.
     * @throws NullPointerException if any argument is null.
     */
    public static DittoCachingSignalEnrichmentFacade newInstance(final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        return new DittoCachingSignalEnrichmentFacade(checkNotNull(cacheLoaderFacade, "cacheLoaderFacade"),
                checkNotNull(cacheConfig, "cacheConfig"),
                checkNotNull(cacheLoaderExecutor, "cacheLoaderExecutor"),
                checkNotNull(cacheNamePrefix, "cacheNamePrefix"));
    }

    @Override
    public CompletionStage<JsonObject> retrieveThing(final EntityId thingId, final List<ThingEvent<?>> events,
            final long minAcceptableSeqNr) {

        if (minAcceptableSeqNr < 0) {
            final var cacheKey =
                    CacheKey.of(thingId, CacheFactory.newCacheLookupContext(DittoHeaders.empty(), null));
            extraFieldsCache.invalidate(cacheKey);
            return doCacheLookup(cacheKey, DittoHeaders.empty());
        } else {
            final var cachingParameters = new CachingParameters(null, events, false, minAcceptableSeqNr);
            return doRetrievePartialThing(thingId, DittoHeaders.empty(), cachingParameters);
        }
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            @Nullable final Signal<?> concernedSignal) {

        // as second step only return what was originally requested as fields:
        final List<Signal<?>> concernedSignals = concernedSignal == null ? List.of() : List.of(concernedSignal);
        final var cachingParameters = new CachingParameters(jsonFieldSelector, concernedSignals, true, 0);
        return doRetrievePartialThing(thingId, dittoHeaders, cachingParameters)
                .thenApply(jsonObject -> jsonObject.get(jsonFieldSelector));
    }

    private CompletionStage<JsonObject> doRetrievePartialThing(final EntityId thingId,
            final DittoHeaders dittoHeaders, final CachingParameters cachingParameters) {

        final var fieldSelector = cachingParameters.fieldSelector;
        final JsonFieldSelector enhancedFieldSelector = enhanceFieldSelectorWithRevision(fieldSelector);

        final var idWithResourceType =
                CacheKey.of(thingId, CacheFactory.newCacheLookupContext(dittoHeaders, enhancedFieldSelector));

        final var cachingParametersWithEnhancedFieldSelector = new CachingParameters(enhancedFieldSelector,
                cachingParameters.concernedSignals,
                cachingParameters.invalidateCacheOnPolicyChange,
                cachingParameters.minAcceptableSeqNr);

        return smartUpdateCachedObject(idWithResourceType, cachingParametersWithEnhancedFieldSelector);
    }

    private static @Nullable
    JsonFieldSelector enhanceFieldSelectorWithRevision(@Nullable final Iterable<JsonPointer> fieldSelector) {
        final JsonFieldSelector result;
        if (fieldSelector == null) {
            result = null;
        } else {
            result = JsonFactory.newFieldSelectorBuilder()
                    .addPointers(fieldSelector)
                    .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                    .build();
        }
        return result;
    }

    private CompletableFuture<JsonObject> smartUpdateCachedObject(final CacheKey idWithResourceType,
            final CachingParameters cachingParameters) {

        final CompletableFuture<JsonObject> result;

        final var invalidateCacheOnPolicyChange = cachingParameters.invalidateCacheOnPolicyChange;
        final var concernedSignals = cachingParameters.concernedSignals;
        final var fieldSelector = cachingParameters.fieldSelector;

        final Optional<List<ThingEvent<?>>> thingEventsOptional =
                extractConsecutiveTwinEvents(concernedSignals, cachingParameters.minAcceptableSeqNr);
        final var dittoHeaders = getLastDittoHeaders(concernedSignals);

        // there are twin events, but their sequence numbers have gaps or do not reach the min acceptable seq nr
        if (thingEventsOptional.isEmpty()) {
            extraFieldsCache.invalidate(idWithResourceType);
            result = doCacheLookup(idWithResourceType, dittoHeaders);
        } else {
            final var thingEvents = thingEventsOptional.orElseThrow();
            // there are no twin events; return the cached thing
            if (thingEvents.isEmpty()) {
                result = doCacheLookup(idWithResourceType, dittoHeaders);
            } else if (thingEventsStartWithLifecycle(thingEvents)) {
                // the twin was created or deleted; continue without revision checks.
                final var nextExpectedThingEventsParameters =
                        new CachingParameters(fieldSelector, thingEvents, invalidateCacheOnPolicyChange,
                                cachingParameters.minAcceptableSeqNr);
                result = handleNextExpectedThingEvents(idWithResourceType, JsonObject.empty(),
                        nextExpectedThingEventsParameters)
                        .toCompletableFuture();
            } else {
                // there are twin events; perform smart update
                result = doCacheLookup(idWithResourceType, dittoHeaders).thenCompose(
                        cachedJsonObject -> doSmartUpdateCachedObject(idWithResourceType, cachedJsonObject,
                                cachingParameters, dittoHeaders));
            }
        }

        return result;
    }

    private static Optional<List<ThingEvent<?>>> extractConsecutiveTwinEvents(
            final Collection<? extends Signal<?>> concernedSignals, final long minAcceptableSeqNr) {

        final List<ThingEvent<?>> thingEvents = concernedSignals.stream()
                .filter(signal -> (signal instanceof ThingEvent) && !(ProtocolAdapter.isLiveSignal(signal)))
                .map(event -> (ThingEvent<?>) event)
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

    private static Optional<Integer> findLastThingDeletedOrCreated(final List<ThingEvent<?>> thingEvents) {
        for (int i = thingEvents.size() - 1; i >= 0; --i) {
            final var event = thingEvents.get(i);
            if (event instanceof ThingDeleted || event instanceof ThingCreated) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static DittoHeaders getLastDittoHeaders(final List<? extends Signal<?>> concernedSignals) {
        if (concernedSignals.isEmpty()) {
            return DittoHeaders.empty();
        } else {
            return getLast(concernedSignals).getDittoHeaders();
        }
    }

    private CompletableFuture<JsonObject> doCacheLookup(final CacheKey idWithResourceType,
            final DittoHeaders dittoHeaders) {

        LOGGER.withCorrelationId(dittoHeaders)
                .debug("Looking up cache entry for <{}>", idWithResourceType);
        return extraFieldsCache.get(idWithResourceType)
                .thenApply(optionalJsonObject -> optionalJsonObject.orElseGet(JsonObject::empty));
    }

    private static boolean thingEventsStartWithLifecycle(final List<ThingEvent<?>> thingEvents) {
        return thingEvents.get(0) instanceof ThingDeleted || thingEvents.get(0) instanceof ThingCreated;
    }

    private CompletionStage<JsonObject> doSmartUpdateCachedObject(final CacheKey idWithResourceType,
            final JsonObject cachedJsonObject,
            final CachingParameters cachingParameters,
            final DittoHeaders dittoHeaders) {

        final CompletionStage<JsonObject> result;

        final long cachedRevision = cachedJsonObject.getValue(Thing.JsonFields.REVISION).orElse(0L);
        final List<ThingEvent<?>> relevantEvents = cachingParameters.concernedSignals.stream()
                .map(event -> (ThingEvent<?>) event)
                .filter(e -> e.getRevision() > cachedRevision)
                .collect(Collectors.toList());

        if (relevantEvents.isEmpty()) {
            // the cache entry was more up-to-date
            result = CompletableFuture.completedFuture(cachedJsonObject);
        } else if (cachedRevision + 1 == getFirst(relevantEvents).getRevision()) {
            // the cache entry was already present and the first thingEvent was the next expected revision no
            // -> we have all information necessary to calculate it without making another roundtrip
            final var nextExpectedThingEventsParameters =
                    new CachingParameters(cachingParameters.fieldSelector, relevantEvents,
                            cachingParameters.invalidateCacheOnPolicyChange,
                            cachingParameters.minAcceptableSeqNr);
            result = handleNextExpectedThingEvents(idWithResourceType, cachedJsonObject,
                    nextExpectedThingEventsParameters);
        } else {
            // the cache entry was already present, but we missed sth and need to invalidate the cache
            // and to another cache lookup (via roundtrip)
            extraFieldsCache.invalidate(idWithResourceType);
            result = doCacheLookup(idWithResourceType, dittoHeaders);
        }
        return result;
    }

    private static <T> T getLast(final List<T> list) {
        return list.get(list.size() - 1);
    }

    private static <T> T getFirst(final List<T> list) {
        return list.get(0);
    }

    private CompletionStage<JsonObject> handleNextExpectedThingEvents(
            final CacheKey idWithResourceType, final JsonObject cachedJsonObject,
            final CachingParameters cachingParameters) {

        final var concernedSignals = (List<ThingEvent<?>>) cachingParameters.concernedSignals;
        final var enhancedFieldSelector = cachingParameters.fieldSelector;
        final Optional<String> cachedPolicyIdOpt = cachedJsonObject.getValue(Thing.JsonFields.POLICY_ID);
        JsonObject jsonObject = cachedJsonObject;
        for (final ThingEvent<?> thingEvent : concernedSignals) {

            switch (thingEvent.getCommandCategory()) {
                case MERGE:
                    jsonObject = getMergeJsonObject(jsonObject, thingEvent);
                    break;
                case DELETE:
                    jsonObject = getDeleteJsonObject(jsonObject, thingEvent);
                    break;
                case MODIFY:
                default:
                    jsonObject = getDefaultJsonObject(jsonObject, thingEvent);
            }
            // invalidate cache on policy change if the flag is set
            if (cachingParameters.invalidateCacheOnPolicyChange) {
                final var optionalCompletionStage =
                        invalidateCacheOnPolicyChange(idWithResourceType, jsonObject, cachedPolicyIdOpt.orElse(null),
                                thingEvent.getDittoHeaders());
                if (optionalCompletionStage.isPresent()) {
                    return optionalCompletionStage.get();
                }
            }
        }
        final var enhancedJsonObject = enhanceJsonObject(jsonObject, concernedSignals, enhancedFieldSelector);
        // update local cache with enhanced object:
        extraFieldsCache.put(idWithResourceType, enhancedJsonObject);
        return CompletableFuture.completedFuture(enhancedJsonObject);
    }

    private static JsonObject getMergeJsonObject(final JsonValue jsonObject, final ThingEvent<?> thingEvent) {
        final var thingMerged = (ThingMerged) thingEvent;
        final JsonValue mergedValue = thingMerged.getValue();
        final JsonObject mergePatch = JsonFactory.newObject(thingMerged.getResourcePath(), mergedValue);
        return JsonFactory.mergeJsonValues(mergePatch, jsonObject).asObject();
    }

    private static JsonObject getDeleteJsonObject(final JsonObject jsonObject, final WithResource thingEvent) {
        final JsonObject result;
        final var resourcePath = thingEvent.getResourcePath();
        if (resourcePath.isEmpty()) {
            result = JsonObject.empty();
        } else {
            result = jsonObject.remove(resourcePath);
        }
        return result;
    }

    private static JsonObject getDefaultJsonObject(final JsonObject jsonObject, final ThingEvent<?> thingEvent) {
        final var resourcePath = thingEvent.getResourcePath();
        final var jsonObjectBuilder = jsonObject.toBuilder();
        final Optional<JsonValue> optEntity = thingEvent.getEntity();
        if (resourcePath.isEmpty() && optEntity.filter(JsonValue::isObject).isPresent()) {
            optEntity.map(JsonValue::asObject).ifPresent(jsonObjectBuilder::setAll);
        } else {
            optEntity.ifPresent(entity -> jsonObjectBuilder
                    .set(resourcePath.toString(), entity)
            );
        }
        return jsonObjectBuilder.build();
    }

    private Optional<CompletionStage<JsonObject>> invalidateCacheOnPolicyChange(final CacheKey idWithResourceType,
            final JsonObject jsonObject,
            @Nullable final String cachedPolicyIdOpt,
            final DittoHeaders dittoHeaders) {

        final boolean shouldInvalidate = Optional.ofNullable(cachedPolicyIdOpt).flatMap(cachedPolicyId ->
                jsonObject.getValue(Thing.JsonFields.POLICY_ID)
                        .filter(currentPolicyId -> !cachedPolicyId.equals(currentPolicyId)))
                .isPresent();
        if (shouldInvalidate) {
            // invalidate the cache
            extraFieldsCache.invalidate(idWithResourceType);
            // and to another cache lookup (via roundtrip):
            return Optional.of(doCacheLookup(idWithResourceType, dittoHeaders));
        } else {
            return Optional.empty();
        }
    }

    private static JsonObject enhanceJsonObject(final JsonObject jsonObject, final List<ThingEvent<?>> concernedSignals,
            final JsonFieldSelector enhancedFieldSelector) {

        final var jsonObjectBuilder = jsonObject.toBuilder()
                .set(Thing.JsonFields.REVISION, getLast(concernedSignals).getRevision());
        return enhancedFieldSelector == null
                ? jsonObjectBuilder.build()
                : jsonObjectBuilder.build().get(enhancedFieldSelector);
    }

    private static final class CachingParameters {

        @Nullable private final JsonFieldSelector fieldSelector;
        private final List<? extends Signal<?>> concernedSignals;
        private final boolean invalidateCacheOnPolicyChange;
        private final long minAcceptableSeqNr;

        private CachingParameters(@Nullable final JsonFieldSelector fieldSelector,
                final List<? extends Signal<?>> concernedSignals,
                final boolean invalidateCacheOnPolicyChange,
                final long minAcceptableSeqNr) {

            this.fieldSelector = fieldSelector;
            this.concernedSignals = concernedSignals;
            this.invalidateCacheOnPolicyChange = invalidateCacheOnPolicyChange;
            this.minAcceptableSeqNr = minAcceptableSeqNr;
        }

    }

}