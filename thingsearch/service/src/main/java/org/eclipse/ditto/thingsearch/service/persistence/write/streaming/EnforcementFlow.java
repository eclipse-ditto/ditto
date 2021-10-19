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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.config.StreamCacheConfig;
import org.eclipse.ditto.thingsearch.service.common.config.StreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.EnforcedThingMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.dispatch.MessageDispatcher;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;

/**
 * Converts Thing changes into write models by retrieving data and applying enforcement via an enforcer cache.
 */
final class EnforcementFlow {

    private static final Source<Entry<Enforcer>, NotUsed> ENFORCER_NONEXISTENT = Source.single(Entry.nonexistent());
    private static final Source<Entry<Policy>, NotUsed> POLICY_NONEXISTENT = Source.single(Entry.nonexistent());
    private static final Source<List<PolicyId>, NotUsed> POLICY_IMPORTS_NONEXISTENT = Source.single(List.of());

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CachingSignalEnrichmentFacade thingsFacade;
    private final Cache<EnforcementCacheKey, Entry<Policy>> policyCache;
    private final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache;
    private final Duration cacheRetryDelay;
    private final int maxArraySize;

    private EnforcementFlow(final ActorRef thingsShardRegion,
            final Cache<EnforcementCacheKey, Entry<Policy>> policyCache,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
            final AskWithRetryConfig askWithRetryConfig,
            final StreamCacheConfig streamCacheConfig,
            final int maxArraySize,
            final MessageDispatcher cacheDispatcher) {

        this.thingsFacade = createThingsFacade(thingsShardRegion, askWithRetryConfig.getAskTimeout(), streamCacheConfig,
                cacheDispatcher);
        this.policyCache = policyCache;
        this.policyEnforcerCache = policyEnforcerCache;
        this.cacheRetryDelay = streamCacheConfig.getRetryDelay();
        this.maxArraySize = maxArraySize;
    }

    /**
     * Create an EnforcementFlow object.
     *
     * @param updaterStreamConfig configuration of the updater stream.
     * @param thingsShardRegion the shard region to retrieve things from.
     * @param policyCache the policy cache to use in order to load policies.
     * @param policyEnforcerCache the policy enforcer cache to use.
     * @param cacheDispatcher dispatcher for the enforcer cache.
     * @return an EnforcementFlow object.
     */
    public static EnforcementFlow of(final StreamConfig updaterStreamConfig,
            final ActorRef thingsShardRegion,
            final Cache<EnforcementCacheKey, Entry<Policy>> policyCache,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
            final MessageDispatcher cacheDispatcher) {

        final var askWithRetryConfig = updaterStreamConfig.getAskWithRetryConfig();
        final var streamCacheConfig = updaterStreamConfig.getCacheConfig();

        return new EnforcementFlow(thingsShardRegion, policyCache, policyEnforcerCache, askWithRetryConfig,
                streamCacheConfig, updaterStreamConfig.getMaxArraySize(), cacheDispatcher);
    }

    private static EnforcementCacheKey getPolicyCacheKey(final PolicyId policyId) {
        return EnforcementCacheKey.of(policyId);
    }

    /**
     * Decide whether to reload an enforcer entry.
     * An entry should be reload if it is out-of-date, nonexistent, or corresponds to a nonexistent entity.
     *
     * @param entry the enforcer cache entry
     * @param metadata the metadata
     * @param policyId the policy ID for which a cache entry should be read - might differ from the one contained in
     * {@code metadata} - if that is the case, a cache reload is never forced.
     * @param iteration how many times cache read was attempted
     * @return whether to reload the cache
     */
    private static boolean shouldReloadCache(@Nullable final Entry<?> entry, final Metadata metadata,
            final EnforcementCacheKey policyId, final int iteration) {

        if (iteration <= 0) {
            // the following will result to "false" when e.g. the "metadata" contains a changed imported policyId,
            // but the "policyId" to load the enforcer for did not change
            final boolean updatedPolicyEqualsPolicyToLoad = metadata.getPolicyId()
                    .filter(updatedPolicyId -> policyId.getId().equals(updatedPolicyId))
                    .isPresent();

            return updatedPolicyEqualsPolicyToLoad && (
                    metadata.shouldInvalidateCache() || entry == null || !entry.exists() ||
                    entry.getRevision() < metadata.getPolicyRevision().orElse(Long.MAX_VALUE)
            );
        } else {
            // never attempt to reload cache more than once
            return false;
        }
    }

    /**
     * Create a flow from Thing changes to write models by retrieving data from Things shard region and enforcer cache.
     *
     * @param shouldAcknowledge defines whether for the created flow the requested ack
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#SEARCH_PERSISTED} was required or not.
     * @param parallelism how many SudoRetrieveThing commands to send in parallel.
     * @return the flow.
     */
    public Flow<Map<ThingId, Metadata>, Source<AbstractWriteModel, NotUsed>, NotUsed> create(
            final boolean shouldAcknowledge, final int parallelism) {
        return Flow.<Map<ThingId, Metadata>>create()
                .map(changeMap -> {
                    log.info("Updating search index with <shouldAcknowledge={}> of <{}> things", shouldAcknowledge,
                            changeMap.size());
                    return sudoRetrieveThingJsons(parallelism, changeMap).flatMapConcat(responseMap ->
                            Source.fromIterator(changeMap.values()::iterator)
                                    .flatMapMerge(parallelism, metadataRef ->
                                            computeWriteModel(metadataRef, responseMap.get(metadataRef.getThingId()))
                                                    .async(MongoSearchUpdaterFlow.DISPATCHER_NAME, parallelism)
                                    )
                    );
                });

    }

    private Source<Map<ThingId, JsonObject>, NotUsed> sudoRetrieveThingJsons(
            final int parallelism, final Map<ThingId, Metadata> changeMap) {

        return Source.fromIterator(changeMap.entrySet()::iterator)
                .flatMapMerge(parallelism, entry -> sudoRetrieveThing(entry)
                        .async(MongoSearchUpdaterFlow.DISPATCHER_NAME, parallelism))
                .<Map<ThingId, JsonObject>>fold(new HashMap<>(), (map, entry) -> {
                    map.put(entry.getKey(), entry.getValue());
                    return map;
                })
                .map(result -> {
                    log.info("Got SudoRetrieveThingResponse <{}> times", result.size());
                    return result;
                });
    }

    private Source<Map.Entry<ThingId, JsonObject>, NotUsed> sudoRetrieveThing(
            final Map.Entry<ThingId, Metadata> entry) {

        final var thingId = entry.getKey();
        final var metadata = entry.getValue();
        ConsistencyLag.startS3RetrieveThing(metadata);
        final CompletionStage<JsonObject> thingFuture;
        if (metadata.shouldInvalidateCache()) {
            thingFuture = thingsFacade.retrieveThing(thingId, List.of(), -1);
        } else {
            thingFuture = thingsFacade.retrieveThing(thingId, metadata.getEvents(), metadata.getThingRevision());
        }

        return Source.completionStage(thingFuture)
                .filter(thing -> !thing.isEmpty())
                .<Map.Entry<ThingId, JsonObject>>map(thing -> new AbstractMap.SimpleImmutableEntry<>(thingId, thing))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<Map.Entry<ThingId, JsonObject>, NotUsed>>()
                        .match(Throwable.class, error -> {
                            log.error("Unexpected response for SudoRetrieveThing " + thingId, error);
                            return Source.empty();
                        })
                        .build());
    }

    private Source<AbstractWriteModel, NotUsed> computeWriteModel(final Metadata metadata,
            @Nullable final JsonObject thing) {

        ConsistencyLag.startS4GetEnforcer(metadata);
        if (thing == null) {
            return Source.single(ThingDeleteModel.of(metadata));
        } else {
            return getEnforcer(metadata, thing)
                    .flatMapConcat(entry ->
                            getPolicyImports(metadata, thing)
                                    .map(imports -> Pair.create(entry, imports))
                    )
                    .map(entryAndPolicies -> {
                        final Entry<Enforcer> enforcerEntry = entryAndPolicies.first();
                        final List<PolicyId> importedPolicyIds = entryAndPolicies.second();

                        if (enforcerEntry.exists()) {
                            try {
                                return EnforcedThingMapper.toWriteModel(thing,
                                        enforcerEntry.getValueOrThrow(),
                                        enforcerEntry.getRevision(),
                                        importedPolicyIds,
                                        maxArraySize,
                                        metadata);
                            } catch (final JsonRuntimeException e) {
                                log.error(e.getMessage(), e);
                                return ThingDeleteModel.of(metadata);
                            }
                        } else {
                            // no enforcer; delete thing from search index
                            return ThingDeleteModel.of(metadata);
                        }
                    });
        }
    }

    /**
     * Get the enforcer of a thing or an empty source if it does not exist.
     *
     * @param metadata metadata of the thing.
     * @param thing the thing
     * @return source of an enforcer or an empty source.
     */
    private Source<Entry<Enforcer>, NotUsed> getEnforcer(final Metadata metadata, final JsonObject thing) {
        try {
            return thing.getValue(Thing.JsonFields.POLICY_ID)
                    .map(PolicyId::of)
                    .map(policyId -> readCachedEnforcer(metadata, getPolicyCacheKey(policyId), 0))
                    .orElse(ENFORCER_NONEXISTENT);
        } catch (PolicyIdInvalidException e) {
            return ENFORCER_NONEXISTENT;
        }
    }

    private Source<Entry<Enforcer>, NotUsed> readCachedEnforcer(final Metadata metadata,
            final EnforcementCacheKey policyId, final int iteration) {

        final Source<Entry<Enforcer>, ?> lazySource = Source.lazySource(() -> {
            final CompletionStage<Source<Entry<Enforcer>, NotUsed>> enforcerFuture = policyEnforcerCache.get(policyId)
                    .thenApply(optionalEnforcerEntry -> {
                        if (shouldReloadCache(optionalEnforcerEntry.orElse(null), metadata, policyId, iteration)) {
                            // invalid entry; invalidate and retry after delay
                            policyEnforcerCache.invalidate(policyId);
                            return readCachedEnforcer(metadata, policyId, iteration + 1)
                                    .initialDelay(cacheRetryDelay);
                        } else {
                            return optionalEnforcerEntry.map(Source::single)
                                    .orElse(ENFORCER_NONEXISTENT);
                        }
                    })
                    .exceptionally(error -> {
                        log.error("Failed to read policyEnforcerCache", error);
                        return ENFORCER_NONEXISTENT;
                    });

            return Source.completionStageSource(enforcerFuture);
        });

        return lazySource.viaMat(Flow.create(), Keep.none());
    }

    private static CachingSignalEnrichmentFacade createThingsFacade(final ActorRef thingsShardRegion,
            final Duration timeout,
            final StreamCacheConfig streamCacheConfig,
            final MessageDispatcher cacheDispatcher) {
        final var sudoRetrieveThingFacade = SudoSignalEnrichmentFacade.of(thingsShardRegion, timeout);
        return CachingSignalEnrichmentFacade.of(sudoRetrieveThingFacade, streamCacheConfig, cacheDispatcher,
                "things-search_enforcementflow_enforcer_cache_things");
    }

    /**
     * Get the imports of a thing's policy or an empty source if it does not exist.
     *
     * @param metadata metadata of the thing.
     * @param thing    the thing
     * @return source of list of policies or an empty source.
     */
    private Source<List<PolicyId>, NotUsed> getPolicyImports(final Metadata metadata, final JsonObject thing) {
        try {
            return thing.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of)
                    .map(policyId -> readCachedImportedPolicies(metadata, EnforcementCacheKey.of(policyId)))
                    .orElse(POLICY_IMPORTS_NONEXISTENT);
        } catch (final PolicyIdInvalidException e) {
            return POLICY_IMPORTS_NONEXISTENT;
        }
    }

    private Source<Entry<Policy>, NotUsed> readCachedPolicy(final Metadata metadata,
            final EnforcementCacheKey policyId, final int iteration) {

        final Source<Entry<Policy>, ?> lazySource = Source.lazySource(() -> {
            final CompletionStage<Source<Entry<Policy>, NotUsed>> policyFuture = policyCache.get(policyId)
                    .thenApply(optionalPolicyEntry -> {
                        if (shouldReloadCache(optionalPolicyEntry.orElse(null), metadata, policyId, iteration)) {
                            // invalid entry; invalidate and retry after delay
                            policyCache.invalidate(policyId);
                            return readCachedPolicy(metadata, policyId, iteration + 1)
                                    .initialDelay(cacheRetryDelay);
                        } else {
                            return optionalPolicyEntry.map(Source::single)
                                    .orElse(POLICY_NONEXISTENT);
                        }
                    })
                    .exceptionally(error -> {
                        log.error("Failed to read policyCache", error);
                        return POLICY_NONEXISTENT;
                    });

            return Source.completionStageSource(policyFuture);
        });

        return lazySource.viaMat(Flow.create(), Keep.none());
    }

    private Source<Optional<PolicyId>, NotUsed> readCachedPolicy(final EnforcementCacheKey policyId) {

        final Source<Optional<PolicyId>, ?> lazySource = Source.lazySource(() -> {
            final CompletionStage<Source<Optional<PolicyId>, NotUsed>> policyFuture =
                    policyCache.get(policyId)
                            .thenApply(optionalPolicyEntry ->
                                            Source.single(optionalPolicyEntry.flatMap(policyEntry -> {
                                                    try {
                                                        return Optional.of(policyEntry
                                                                .getValueOrThrow()
                                                                .getEntityId()
                                                                .orElseThrow()
                                                        );
                                                    } catch (final NoSuchElementException e) {
                                                        return Optional.empty();
                                                    }
                                            }))
                            )
                            .exceptionally(error -> {
                                log.error("Failed to read policyCache", error);
                                return Source.single(Optional.empty());
                            });

            return Source.completionStageSource(policyFuture);
        });

        return lazySource.viaMat(Flow.create(), Keep.none());
    }

    private Source<List<PolicyId>, NotUsed> readCachedImportedPolicies(final Metadata metadata,
            final EnforcementCacheKey policyId) {

        return readCachedPolicy(metadata, policyId, 0).flatMapConcat(policyEntry -> {
            final Policy policy;
            try {
                policy = policyEntry.getValueOrThrow();
            } catch (NoSuchElementException e) {
                return POLICY_IMPORTS_NONEXISTENT;
            }

            final Optional<PolicyImports> optionalPolicyImports = policy.getImports();
            if (optionalPolicyImports.isEmpty()) {
                return POLICY_IMPORTS_NONEXISTENT;
            }

            final PolicyImports policyImports = optionalPolicyImports.get();
            final Source<Optional<PolicyId>, NotUsed> policySource =
                    Source.from(policyImports)
                            .flatMapConcat(policyImport -> readCachedPolicy(
                                    EnforcementCacheKey.of(policyImport.getImportedPolicyId())));

            return policySource.grouped(policyImports.getSize()).map(entries ->
                    entries.stream().filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList())
            );
        });
    }

}
