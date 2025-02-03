/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
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
public class DittoCachingSignalEnrichmentFacade implements CachingSignalEnrichmentFacade {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DittoCachingSignalEnrichmentFacade.class);
    private static final String CACHE_NAME_SUFFIX = "_signal_enrichment_cache";

    protected final Cache<SignalEnrichmentCacheKey, JsonObject> extraFieldsCache;

    protected DittoCachingSignalEnrichmentFacade(
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        final var cacheLoader = SignalEnrichmentCacheLoader.of(cacheLoaderFacade);
        final var cacheName = cacheNamePrefix + CACHE_NAME_SUFFIX;

        extraFieldsCache = CacheFactory.createCache(cacheLoader, cacheConfig, cacheName, cacheLoaderExecutor);
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
    public CompletionStage<JsonObject> retrieveThing(final ThingId thingId, final List<ThingEvent<?>> events,
            final long atRevisionNumber) {

        final DittoHeaders dittoHeadersNotAddedToCacheKey =
                buildDittoHeadersNotAddedToCacheKey(events, atRevisionNumber);

        final JsonFieldSelector fieldSelector = determineSelector(thingId.getNamespace());

        if (atRevisionNumber < 0) {
            final var cacheKey = SignalEnrichmentCacheKey.of(
                    thingId,
                    SignalEnrichmentContext.of(DittoHeaders.empty(), dittoHeadersNotAddedToCacheKey, fieldSelector));
            extraFieldsCache.invalidate(cacheKey);
            return doCacheLookup(cacheKey, dittoHeadersNotAddedToCacheKey);
        } else {
            final var cachingParameters =
                    new CachingParameters(fieldSelector, events, false, atRevisionNumber);

            return doRetrievePartialThing(thingId, DittoHeaders.empty(), dittoHeadersNotAddedToCacheKey,
                    cachingParameters);
        }
    }

    private static DittoHeaders buildDittoHeadersNotAddedToCacheKey(final List<ThingEvent<?>> events,
            final long atRevisionNumber) {
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = DittoHeaders.newBuilder();
        if (!events.isEmpty()) {
            dittoHeadersBuilder.correlationId(
                    events.getLast().getDittoHeaders()
                            .getCorrelationId()
                            .orElseGet(() -> UUID.randomUUID().toString())
                            + "-enrichment"
            );
        }

        if (atRevisionNumber > 0) {
            dittoHeadersBuilder
                    .putHeader(DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), String.valueOf(atRevisionNumber));
        }

        final var startedSpan = DittoTracing.newPreparedSpan(
                        dittoHeadersBuilder.build(),
                        SpanOperationName.of("caching_enrichment_facade_retrieve_thing")
                )
                .start();

        return DittoHeaders.of(startedSpan.propagateContext(dittoHeadersBuilder.build()));
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            @Nullable final JsonFieldSelector jsonFieldSelector, final DittoHeaders dittoHeaders,
            @Nullable final Signal<?> concernedSignal) {

        final List<ThingEvent<?>> thingEvents =
                (concernedSignal instanceof ThingEvent) && !(ProtocolAdapter.isLiveSignal(concernedSignal)) ?
                        List.of((ThingEvent<?>) concernedSignal) : List.of();

        final DittoHeaders signalHeaders = Optional.ofNullable(concernedSignal)
                .map(Signal::getDittoHeaders)
                .orElseGet(DittoHeaders::empty);
        if (jsonFieldSelector != null &&
                signalHeaders.containsKey(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey())
        ) {
            return performPreDefinedExtraFieldsOptimization(
                    thingId, jsonFieldSelector, dittoHeaders, signalHeaders, thingEvents
            );
        } else {
            // as second step only return what was originally requested as fields:
            final var cachingParameters =
                    new CachingParameters(jsonFieldSelector, thingEvents, true, 0);
            return doRetrievePartialThing(thingId, dittoHeaders, null, cachingParameters)
                    .thenApply(jsonObject -> applyJsonFieldSelector(jsonObject, jsonFieldSelector));
        }
    }

    private CompletionStage<JsonObject> performPreDefinedExtraFieldsOptimization(final ThingId thingId,
            final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            final DittoHeaders signalHeaders,
            final List<ThingEvent<?>> thingEvents
    ) {
        final JsonArray configuredPredefinedExtraFields =
                JsonArray.of(signalHeaders.get(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey()));
        final Set<JsonPointer> allConfiguredPredefinedExtraFields = configuredPredefinedExtraFields.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(JsonPointer::of)
                .collect(Collectors.toSet());

        final JsonObject preDefinedExtraFields =
                JsonObject.of(signalHeaders.get(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey()));
        final JsonObject filteredPreDefinedExtraFieldsReadGranted =
                filterPreDefinedExtraReadGrantedObject(jsonFieldSelector, dittoHeaders, signalHeaders,
                        preDefinedExtraFields);

        final boolean allExtraFieldsPresent =
                allConfiguredPredefinedExtraFields.containsAll(jsonFieldSelector.getPointers());
        if (allExtraFieldsPresent) {
            LOGGER.withCorrelationId(dittoHeaders)
                    .debug("All asked for extraFields for thing <{}> were present in pre-defined fields, " +
                            "skipping cache retrieval: <{}>", thingId, jsonFieldSelector);
            return CompletableFuture.completedStage(filteredPreDefinedExtraFieldsReadGranted);
        } else {
            // optimization to only fetch extra fields which were not pre-defined
            final List<JsonPointer> missingFieldsPointers = new ArrayList<>(jsonFieldSelector.getPointers());
            missingFieldsPointers.removeAll(allConfiguredPredefinedExtraFields);
            final JsonFieldSelector missingFieldsSelector = JsonFactory.newFieldSelector(missingFieldsPointers);
            final var cachingParameters =
                    new CachingParameters(missingFieldsSelector, thingEvents, true, 0);

            LOGGER.withCorrelationId(dittoHeaders)
                    .debug("Fetching non pre-defined extraFields for thing <{}>: <{}>", thingId, missingFieldsPointers);

            return doRetrievePartialThing(thingId, dittoHeaders, null, cachingParameters)
                    .thenApply(jsonObject ->
                            JsonFactory.newObject( // merge
                                    applyJsonFieldSelector(jsonObject, missingFieldsSelector),
                                    filteredPreDefinedExtraFieldsReadGranted
                            )
                    );
        }
    }

    private static JsonObject filterPreDefinedExtraReadGrantedObject(
            final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            final DittoHeaders signalHeaders,
            final JsonObject preDefinedExtraFields
    ) {
        final JsonObject preDefinedExtraFieldsReadGrant = JsonObject.of(
                signalHeaders.get(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey())
        );
        final JsonFieldSelector grantedReadJsonFieldSelector = filterAskedForFieldSelectorToGrantedFields(
                jsonFieldSelector,
                preDefinedExtraFieldsReadGrant,
                dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds()
        );
        return preDefinedExtraFields.get(grantedReadJsonFieldSelector);
    }

    private static JsonFieldSelector filterAskedForFieldSelectorToGrantedFields(
            final JsonFieldSelector jsonFieldSelector,
            final JsonObject preDefinedExtraFieldsReadGrant,
            final List<String> authorizationSubjectIds
    ) {
        final List<JsonValue> authSubjects = authorizationSubjectIds.stream().map(JsonValue::of).toList();
        final JsonObject scopedPreDefinedExtraFieldsReadGrant = preDefinedExtraFieldsReadGrant.stream()
                .filter(field -> field.getValue().asArray().stream().anyMatch(authSubjects::contains))
                .collect(JsonCollectors.fieldsToObject());
        final List<JsonPointer> allowedPointers = scopedPreDefinedExtraFieldsReadGrant.getKeys().stream()
                .filter(key -> jsonFieldSelector.getPointers().stream()
                        .anyMatch(p -> key.toString().startsWith(p.toString()))
                )
                .map(key -> JsonPointer.of(key.toString().substring(1)))
                .toList();
        return JsonFactory.newFieldSelector(allowedPointers);
    }

    protected CompletionStage<JsonObject> doRetrievePartialThing(final EntityId thingId,
            final DittoHeaders dittoHeaders,
            @Nullable final DittoHeaders dittoHeadersNotAddedToCacheKey,
            final CachingParameters cachingParameters) {

        final var fieldSelector = cachingParameters.fieldSelector;
        final JsonFieldSelector enhancedFieldSelector = enhanceFieldSelectorWithRevision(fieldSelector);

        final var idWithResourceType = SignalEnrichmentCacheKey.of(
                thingId,
                SignalEnrichmentContext.of(dittoHeaders, dittoHeadersNotAddedToCacheKey, enhancedFieldSelector)
        );

        final var cachingParametersWithEnhancedFieldSelector = new CachingParameters(enhancedFieldSelector,
                cachingParameters.concernedEvents,
                cachingParameters.invalidateCacheOnPolicyChange,
                cachingParameters.minAcceptableSeqNr);

        return smartUpdateCachedObject(idWithResourceType, cachingParametersWithEnhancedFieldSelector);
    }

    @Nullable
    private static JsonFieldSelector enhanceFieldSelectorWithRevision(
            @Nullable final Iterable<JsonPointer> fieldSelector) {
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

    private CompletableFuture<JsonObject> smartUpdateCachedObject(final SignalEnrichmentCacheKey cacheKey,
            final CachingParameters cachingParameters) {

        final CompletableFuture<JsonObject> result;

        final var invalidateCacheOnPolicyChange = cachingParameters.invalidateCacheOnPolicyChange;
        final var concernedSignals = cachingParameters.concernedEvents;
        final var fieldSelector = cachingParameters.fieldSelector;

        final Optional<List<ThingEvent<?>>> thingEventsOptional =
                extractConsecutiveTwinEvents(concernedSignals, cachingParameters.minAcceptableSeqNr);
        final var dittoHeaders = getLastDittoHeaders(concernedSignals);

        // there are twin events, but their sequence numbers have gaps or do not reach the min acceptable seq nr
        if (thingEventsOptional.isEmpty()) {
            extraFieldsCache.invalidate(cacheKey);
            result = doCacheLookup(cacheKey, dittoHeaders);
        } else {
            final var thingEvents = thingEventsOptional.orElseThrow();
            // there are no twin events; return the cached thing
            if (thingEvents.isEmpty()) {
                result = doCacheLookup(cacheKey, dittoHeaders);
            } else if (thingEventsStartWithCreated(thingEvents)) {
                // the twin was created; continue without revision checks.
                final var nextExpectedThingEventsParameters =
                        new CachingParameters(fieldSelector, thingEvents, invalidateCacheOnPolicyChange,
                                cachingParameters.minAcceptableSeqNr);
                result = doCacheLookup(cacheKey, dittoHeaders).thenCompose(
                        cachedJsonObject -> handleNextExpectedThingEvents(cacheKey, cachedJsonObject,
                                nextExpectedThingEventsParameters));
            } else {
                // there are twin events; perform smart update
                result = doCacheLookup(cacheKey, dittoHeaders).thenCompose(
                        cachedJsonObject -> doSmartUpdateCachedObject(cacheKey, cachedJsonObject,
                                cachingParameters, dittoHeaders));
            }
        }

        return result;
    }

    private static Optional<List<ThingEvent<?>>> extractConsecutiveTwinEvents(
            final List<ThingEvent<?>> thingEvents, final long minAcceptableSeqNr) {

        // ignore events before ThingDeleted or ThingCreated
        // not safe to ignore events before ThingModified because ThingModified has merge semantics at the top level
        final var events = findLastThingDeletedOrCreated(thingEvents)
                .map(i -> thingEvents.subList(i, thingEvents.size()))
                .orElse(thingEvents);

        // Check if minimum acceptable sequence number is met
        if (minAcceptableSeqNr < 0 || events.isEmpty() || getLast(events).getRevision() < minAcceptableSeqNr) {
            return Optional.empty();
        }

        // Validate sequence numbers. Discard if events have gaps
        long lastSeq = -1;
        for (final ThingEvent<?> event : events) {
            if (lastSeq >= 0 && event.getRevision() != lastSeq + 1) {
                return Optional.empty();
            } else {
                lastSeq = event.getRevision();
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

    protected CompletableFuture<JsonObject> doCacheLookup(final SignalEnrichmentCacheKey cacheKey,
            final DittoHeaders dittoHeaders) {
        LOGGER.withCorrelationId(dittoHeaders).debug("Looking up cache entry for <{}>", cacheKey);

        return extraFieldsCache.get(cacheKey)
                .thenApply(optionalJsonObject -> optionalJsonObject.orElseGet(JsonObject::empty));
    }

    private static boolean thingEventsStartWithCreated(final List<ThingEvent<?>> thingEvents) {
        return thingEvents.get(0) instanceof ThingCreated;
    }

    private CompletionStage<JsonObject> doSmartUpdateCachedObject(final SignalEnrichmentCacheKey cacheKey,
            final JsonObject cachedJsonObject, final CachingParameters cachingParameters,
            final DittoHeaders dittoHeaders) {

        final CompletionStage<JsonObject> result;

        final long cachedRevision = cachedJsonObject.getValue(Thing.JsonFields.REVISION).orElse(0L);
        final List<ThingEvent<?>> relevantEvents = cachingParameters.concernedEvents.stream()
                .filter(e -> e.getRevision() > cachedRevision)
                .toList();

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
            result = handleNextExpectedThingEvents(cacheKey, cachedJsonObject,
                    nextExpectedThingEventsParameters);
        } else {
            // the cache entry was already present, but we missed sth and need to invalidate the cache
            // and to another cache lookup (via roundtrip)
            extraFieldsCache.invalidate(cacheKey);
            result = doCacheLookup(cacheKey, dittoHeaders);
        }

        return result;
    }

    private static <T> T getLast(final List<T> list) {
        return list.getLast();
    }

    private static <T> T getFirst(final List<T> list) {
        return list.getFirst();
    }

    private CompletionStage<JsonObject> handleNextExpectedThingEvents(final SignalEnrichmentCacheKey cacheKey,
            final JsonObject cachedJsonObject, final CachingParameters cachingParameters) {

        final var concernedSignals = cachingParameters.concernedEvents;
        final var enhancedFieldSelector = cachingParameters.fieldSelector;
        final Optional<String> cachedPolicyIdOpt = cachedJsonObject.getValue(Thing.JsonFields.POLICY_ID);
        JsonObject jsonObject = cachedJsonObject;
        for (final ThingEvent<?> thingEvent : concernedSignals) {

            jsonObject = switch (thingEvent.getCommandCategory()) {
                case MERGE -> getMergeJsonObject(jsonObject, thingEvent);
                case DELETE -> getDeleteJsonObject(jsonObject, thingEvent);
                default -> getDefaultJsonObject(jsonObject, thingEvent);
            };
            // invalidate cache on policy change if the flag is set
            if (cachingParameters.invalidateCacheOnPolicyChange) {
                final var optionalCompletionStage =
                        invalidateCacheOnPolicyChange(cacheKey, jsonObject, cachedPolicyIdOpt.orElse(null),
                                thingEvent.getDittoHeaders());
                if (optionalCompletionStage.isPresent()) {
                    return optionalCompletionStage.get();
                }
            }
        }
        final var enhancedJsonObject = enhanceJsonObject(jsonObject, concernedSignals, enhancedFieldSelector);
        // update local cache with enhanced object:
        extraFieldsCache.put(cacheKey, enhancedJsonObject);

        return CompletableFuture.completedFuture(enhancedJsonObject);
    }

    private static JsonObject getMergeJsonObject(final JsonValue jsonObject, final ThingEvent<?> thingEvent) {
        final var thingMerged = (ThingMerged) thingEvent;
        final JsonValue mergedValue = thingMerged.getValue();
        final JsonObjectBuilder mergePatchBuilder = JsonFactory.newObject(thingMerged.getResourcePath(), mergedValue)
                .toBuilder();
        thingMerged.getMetadata()
                .ifPresent(metadata -> mergePatchBuilder.set(
                                Thing.JsonFields.METADATA.getPointer().append(thingMerged.getResourcePath()), metadata)
                        .build());

        return JsonFactory.mergeJsonValues(mergePatchBuilder.build(), jsonObject).asObject();
    }

    private static JsonObject getDeleteJsonObject(final JsonObject jsonObject, final WithResource thingEvent) {
        final JsonObject result;
        final var resourcePath = thingEvent.getResourcePath();
        if (thingEvent instanceof ThingDeleted) {
            // NoOp because we just want to keep the original known thing.
            result = jsonObject;
        } else if (resourcePath.isEmpty()) {
            result = JsonObject.empty();
        } else {
            result =
                    jsonObject.remove(resourcePath).remove(Thing.JsonFields.METADATA.getPointer().append(resourcePath));
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
            optEntity.ifPresent(entity -> jsonObjectBuilder.set(resourcePath.toString(), entity)
            );
        }
        thingEvent.getMetadata().ifPresent(
                metadata -> jsonObjectBuilder.set(Thing.JsonFields.METADATA.getPointer().append(resourcePath),
                        metadata));

        return jsonObjectBuilder.build();
    }

    private Optional<CompletionStage<JsonObject>> invalidateCacheOnPolicyChange(final SignalEnrichmentCacheKey cacheKey,
            final JsonObject jsonObject, @Nullable final String cachedPolicyIdOpt, final DittoHeaders dittoHeaders) {

        final boolean shouldInvalidate = Optional.ofNullable(cachedPolicyIdOpt).flatMap(cachedPolicyId ->
                        jsonObject.getValue(Thing.JsonFields.POLICY_ID)
                                .filter(currentPolicyId -> !cachedPolicyId.equals(currentPolicyId)))
                .isPresent();
        if (shouldInvalidate) {
            // invalidate the cache
            extraFieldsCache.invalidate(cacheKey);
            // and to another cache lookup (via roundtrip):
            return Optional.of(doCacheLookup(cacheKey, dittoHeaders));
        } else {
            return Optional.empty();
        }
    }

    private JsonObject enhanceJsonObject(final JsonObject jsonObject, final List<ThingEvent<?>> concernedSignals,
            @Nullable final JsonFieldSelector enhancedFieldSelector) {

        final ThingEvent<?> last = getLast(concernedSignals);
        final var jsonObjectBuilder = jsonObject.toBuilder()
                .set(Thing.JsonFields.REVISION, last.getRevision());
        concernedSignals.stream()
                .filter(ThingCreated.class::isInstance)
                .map(ThingCreated.class::cast)
                .forEach(thingCreated -> thingCreated.getTimestamp().ifPresent(timestamp ->
                        jsonObjectBuilder.set(Thing.JsonFields.CREATED, timestamp.toString())));
        last.getTimestamp().ifPresent(timestamp ->
                jsonObjectBuilder.set(Thing.JsonFields.MODIFIED, timestamp.toString()));

        return applyJsonFieldSelector(jsonObjectBuilder.build(), enhancedFieldSelector);
    }

    @Nullable
    protected JsonFieldSelector determineSelector(final String namespace) {
        // By default, we do not return a field selector.
        return null;
    }

    protected static final class CachingParameters {

        @Nullable private final JsonFieldSelector fieldSelector;
        private final List<ThingEvent<?>> concernedEvents;
        private final boolean invalidateCacheOnPolicyChange;
        private final long minAcceptableSeqNr;

        public CachingParameters(@Nullable final JsonFieldSelector fieldSelector,
                final List<ThingEvent<?>> concernedEvents,
                final boolean invalidateCacheOnPolicyChange,
                final long minAcceptableSeqNr) {

            this.fieldSelector = fieldSelector;
            this.concernedEvents = concernedEvents;
            this.invalidateCacheOnPolicyChange = invalidateCacheOnPolicyChange;
            this.minAcceptableSeqNr = minAcceptableSeqNr;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "fieldSelector=" + fieldSelector +
                    ", concernedEvents=" + concernedEvents +
                    ", invalidateCacheOnPolicyChange=" + invalidateCacheOnPolicyChange +
                    ", minAcceptableSeqNr=" + minAcceptableSeqNr +
                    "]";
        }
    }

}
