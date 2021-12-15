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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacadeProvider;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.config.StreamCacheConfig;
import org.eclipse.ditto.thingsearch.service.common.config.StreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.EnforcedThingMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.updater.actors.SearchUpdateObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Scheduler;
import akka.dispatch.MessageDispatcher;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;

/**
 * Converts Thing changes into write models by retrieving data and applying enforcement via an enforcer cache.
 */
final class EnforcementFlow {

    private static final Source<Entry<Enforcer>, NotUsed> ENFORCER_NONEXISTENT = Source.single(Entry.nonexistent());

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CachingSignalEnrichmentFacade thingsFacade;
    private final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache;
    private final Duration cacheRetryDelay;
    private final int maxArraySize;
    private final SearchUpdateObserver searchUpdateObserver;

    private EnforcementFlow(final ActorSystem actorSystem,
            final ActorRef thingsShardRegion,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
            final AskWithRetryConfig askWithRetryConfig,
            final StreamCacheConfig streamCacheConfig,
            final int maxArraySize,
            final Executor cacheDispatcher) {

        thingsFacade = createThingsFacade(actorSystem, thingsShardRegion, askWithRetryConfig.getAskTimeout(),
                streamCacheConfig, cacheDispatcher);
        this.policyEnforcerCache = policyEnforcerCache;
        searchUpdateObserver = SearchUpdateObserver.get(actorSystem);
        cacheRetryDelay = streamCacheConfig.getRetryDelay();
        this.maxArraySize = maxArraySize;
    }

    /**
     * Create an EnforcementFlow object.
     *
     * @param actorSystem the actor system for loading the {@link CachingSignalEnrichmentFacadeProvider}
     * @param updaterStreamConfig configuration of the updater stream.
     * @param thingsShardRegion the shard region to retrieve things from.
     * @param policiesShardRegion the shard region to retrieve policies from.
     * @param cacheDispatcher dispatcher for the enforcer cache.
     * @param scheduler the scheduler to use for retrying timed out asks for the policy enforcer cache loader.
     * @return an EnforcementFlow object.
     */
    public static EnforcementFlow of(final ActorSystem actorSystem,
            final StreamConfig updaterStreamConfig,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final MessageDispatcher cacheDispatcher,
            final Scheduler scheduler) {

        final var askWithRetryConfig = updaterStreamConfig.getAskWithRetryConfig();
        final var streamCacheConfig = updaterStreamConfig.getCacheConfig();

        final AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askWithRetryConfig, scheduler, policiesShardRegion);
        final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, streamCacheConfig,
                                "things-search_enforcementflow_enforcer_cache_policy", cacheDispatcher)
                        .projectValues(PolicyEnforcer::project, PolicyEnforcer::embed);

        return new EnforcementFlow(actorSystem, thingsShardRegion, policyEnforcerCache, askWithRetryConfig,
                streamCacheConfig, updaterStreamConfig.getMaxArraySize(), cacheDispatcher);
    }

    private static EnforcementCacheKey getPolicyCacheKey(final PolicyId policyId) {
        return EnforcementCacheKey.of(policyId);
    }

    /**
     * Decide whether to reload an enforcer entry.
     * An entry should be reloaded if it is out-of-date, nonexistent, or corresponds to a nonexistent entity.
     *
     * @param entry the enforcer cache entry
     * @param metadata the metadata
     * @param iteration how many times cache read was attempted
     * @return whether to reload the cache
     */
    private static boolean shouldReloadCache(@Nullable final Entry<?> entry, final Metadata metadata,
            final int iteration) {

        if (iteration <= 0) {
            return metadata.shouldInvalidatePolicy() || entry == null || !entry.exists() ||
                    entry.getRevision() < metadata.getPolicyRevision().orElse(Long.MAX_VALUE);
        } else {
            // never attempt to reload cache more than once
            return false;
        }
    }

    /**
     * Create a flow from Thing changes to write models by retrieving data from Things shard region and enforcer cache.
     *
     * @param parallelism how many SudoRetrieveThing commands to send in parallel.
     * @return the flow.
     */
    public Flow<Map<ThingId, Metadata>, Source<AbstractWriteModel, NotUsed>, NotUsed> create(final int parallelism) {

        return Flow.<Map<ThingId, Metadata>>create()
                .map(changeMap -> {
                    log.info("Updating search index of <{}> things", changeMap.size());
                    return sudoRetrieveThingJsons(parallelism, changeMap).flatMapConcat(responseMap ->
                            Source.fromIterator(changeMap.values()::iterator)
                                    .flatMapMerge(parallelism, metadataRef -> {
                                                final JsonObject thing = responseMap.get(metadataRef.getThingId());
                                                searchUpdateObserver.process(metadataRef, thing);
                                                return computeWriteModel(metadataRef, thing)
                                                        .async(MongoSearchUpdaterFlow.DISPATCHER_NAME, parallelism);
                                            }
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
        if (metadata.shouldInvalidateThing()) {
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
                    .map(entry -> {
                        if (entry.exists()) {
                            try {
                                return EnforcedThingMapper.toWriteModel(thing, entry.getValueOrThrow(),
                                        entry.getRevision(),
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
        } catch (final PolicyIdInvalidException e) {
            return ENFORCER_NONEXISTENT;
        }
    }

    private Source<Entry<Enforcer>, NotUsed> readCachedEnforcer(final Metadata metadata,
            final EnforcementCacheKey policyId, final int iteration) {

        final Source<Entry<Enforcer>, ?> lazySource = Source.lazySource(() -> {
            final CompletionStage<Source<Entry<Enforcer>, NotUsed>> enforcerFuture = policyEnforcerCache.get(policyId)
                    .thenApply(optionalEnforcerEntry -> {
                        if (shouldReloadCache(optionalEnforcerEntry.orElse(null), metadata, iteration)) {
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

    private static CachingSignalEnrichmentFacade createThingsFacade(final ActorSystem actorSystem,
            final ActorRef thingsShardRegion,
            final Duration timeout,
            final CacheConfig streamCacheConfig,
            final Executor cacheDispatcher) {

        final var sudoRetrieveThingFacade = SudoSignalEnrichmentFacade.of(thingsShardRegion, timeout);
        final var cachingSignalEnrichmentFacadeProvider = CachingSignalEnrichmentFacadeProvider.get(actorSystem);
        return cachingSignalEnrichmentFacadeProvider.getSignalEnrichmentFacade(actorSystem, sudoRetrieveThingFacade,
                streamCacheConfig, cacheDispatcher, "things-search_enforcementflow_enforcer_cache_things");
    }

}
