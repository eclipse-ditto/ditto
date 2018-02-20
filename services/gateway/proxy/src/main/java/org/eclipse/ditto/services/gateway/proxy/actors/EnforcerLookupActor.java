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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.distributedcache.actors.ModifyCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntryResponse;
import org.eclipse.ditto.services.utils.distributedcache.actors.WithContext;
import org.eclipse.ditto.services.utils.distributedcache.actors.WriteConsistency;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor responsible for looking up which enforcer ({@link PolicyEnforcerActor} or {@link AclEnforcerActor}) shard
 * region with which shardId to use.
 */
public final class EnforcerLookupActor extends AbstractActor {

    private static final String ACTOR_NAME_SUFFIX = "EnforcerLookup";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef aclEnforcerShardRegion;
    private final ActorRef policyEnforcerShardRegion;
    private final ActorRef cacheFacade;
    private final EnforcerLookupFunction enforcerLookupFunction;

    private EnforcerLookupActor(final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef cacheFacade,
            final EnforcerLookupFunction enforcerLookupFunction) {
        this.aclEnforcerShardRegion = aclEnforcerShardRegion;
        this.policyEnforcerShardRegion = policyEnforcerShardRegion;
        this.cacheFacade = cacheFacade;
        this.enforcerLookupFunction = enforcerLookupFunction;
    }

    /**
     * Creates Akka configuration object Props for this {@code EnforcerLookupActor}.
     *
     * @param aclEnforcerShardRegion the Actor ref of the ACL enforcer shard region.
     * @param policyEnforcerShardRegion the Actor ref of the policy enforcer shard region.
     * @param cacheFacade the Actor ref to the distributed cache facade.
     * @param enforcerLookupFunction the strategy to lookup entities which are not cached yet.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef cacheFacade,
            final EnforcerLookupFunction enforcerLookupFunction) {
        return Props.create(EnforcerLookupActor.class, new Creator<EnforcerLookupActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public EnforcerLookupActor create() throws Exception {
                return new EnforcerLookupActor(aclEnforcerShardRegion, policyEnforcerShardRegion, cacheFacade,
                        enforcerLookupFunction);
            }
        });
    }

    /**
     * Returns a name for this Actor suffixed with {@link EnforcerLookupActor#ACTOR_NAME_SUFFIX}.
     *
     * @param namePrefix the name prefix.
     * @return the actor name.
     */
    public static String actorNameFor(final CharSequence namePrefix) {
        return namePrefix + ACTOR_NAME_SUFFIX;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(LookupEnforcer.class, this::handleLookupEnforcer)
                .match(RetrieveCacheEntryResponse.class, EnforcerLookupActor::hasLookupContext,
                        this::handleValidRetrieveCacheEntryResponse)
                .match(RetrieveCacheEntryResponse.class,
                        entryResponse -> log.error("Context is not of type 'LookupContext': {}",
                                entryResponse.getContext().orElse(null)))
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        cause = new DittoJsonException((RuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .match(DittoRuntimeException.class, cre -> getSender().tell(cre, getSelf()))
                .matchAny(m -> log.warning("Got unknown message, expected a 'LookupEnforcer': {}", m))
                .build();
    }

    private static boolean hasLookupContext(final WithContext withContext) {
        return withContext.getContext()
                .map(Object::getClass)
                .filter(LookupContext.class::isAssignableFrom)
                .isPresent();
    }

    private void handleLookupEnforcer(final LookupEnforcer lookupEnforcer) {
        log.debug("Looking up enforcer for ID '{}' with ReadConsistency '{}'", lookupEnforcer.getId(),
                lookupEnforcer.getReadConsistency());
        cacheFacade.tell(new RetrieveCacheEntry(lookupEnforcer.getId(), lookupEnforcer.getContext(),
                lookupEnforcer.getReadConsistency()), getSelf());
    }

    private void handleValidRetrieveCacheEntryResponse(final RetrieveCacheEntryResponse entryResponse) {
        final String id = entryResponse.getId();
        final LookupContext<?> lookupContext = entryResponse.getContext()
                .map(LookupContext.class::cast)
                .orElseThrow(IllegalStateException::new);

        final Optional<CacheEntry> cacheEntryOpt = entryResponse.getCacheEntry();
        if (cacheEntryOpt.isPresent() && !cacheEntryOpt.get().isDeleted()) {
            final CacheEntry cacheEntry = cacheEntryOpt.get();
            log.debug("CacheEntry found for ID <{}>: {}", id, cacheEntry);
            handleCacheEntryFoundResponse(id, lookupContext, cacheEntry);
        } else {
            // cache entry not found or was deleted
            log.debug("No CacheEntry found for ID <{}>.", id);
            final CacheEntry cacheEntry = cacheEntryOpt.orElse(null);
            handleNoCacheEntryFoundResponse(id, lookupContext, cacheEntry);
        }
    }

    private void handleNoCacheEntryFoundResponse(final String id, final LookupContext<?> lookupContext,
            @Nullable final CacheEntry cacheEntry) {
        final Signal<?> signal = lookupContext.getInitialCommandOrEvent();
        final String correlationId = signal.getDittoHeaders()
                .getCorrelationId()
                .orElse(UUID.randomUUID().toString());

        final ActorRef self = getSelf();

        // cache entry does not exist. retrieves the actual data from relevant microservices and
        // save the result in the cache. no data changes, so WriteConsistency.LOCAL is sufficient.
        enforcerLookupFunction.lookup(id, correlationId)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable, "Got Throwable while looking up enforcer: {}",
                                throwable.getMessage());
                    } else {
                        if (result.getError().isPresent()) {
                            final LookupEnforcerResponse response = new LookupEnforcerResponse(null, null,
                                    lookupContext, cacheEntry, result.getError().get());

                            final ActorRef lookupRecipient = lookupContext.getLookupRecipient();
                            lookupRecipient.tell(response, self);
                        } else {
                            result.getCacheEntry()
                                    .map(resultCacheEntry ->
                                            new ModifyCacheEntry(id, resultCacheEntry, WriteConsistency.LOCAL))
                                    .ifPresent(modifyCacheEntry ->
                                            cacheFacade.tell(modifyCacheEntry, ActorRef.noSender()));

                            final LookupEnforcerResponse response = new LookupEnforcerResponse(
                                    result.getActorRef().orElse(null),
                                    result.getShardId().orElse(null),
                                    lookupContext,
                                    cacheEntry);

                            final ActorRef lookupRecipient = lookupContext.getLookupRecipient();
                            lookupRecipient.tell(response, self);
                        }
                    }
                })
                .exceptionally(error -> {
                    log.error(error, "Exception thrown while processing lookup enforcer result: {}",
                            error.getMessage());
                    return null;
                });
    }

    private void handleCacheEntryFoundResponse(final String id, final LookupContext<?> lookupContext,
            final CacheEntry cacheEntry) {
        final boolean hasJsonSchemaVersion1 = cacheEntry.getJsonSchemaVersion()
                .filter(JsonSchemaVersion.V_1::equals)
                .isPresent();
        final LookupEnforcerResponse response;
        if (hasJsonSchemaVersion1) {
            // ACL-based in schema version 1:
            response =
                    new LookupEnforcerResponse(aclEnforcerShardRegion, id, lookupContext, cacheEntry);
        } else {
            response = cacheEntry.getPolicyId()
                    .map(policyId -> new LookupEnforcerResponse(policyEnforcerShardRegion, policyId,
                            lookupContext, cacheEntry))
                    .orElseGet(() -> new LookupEnforcerResponse(null, id, lookupContext, cacheEntry));
        }

        final ActorRef lookupRecipient = lookupContext.getLookupRecipient();
        lookupRecipient.tell(response, getSelf());
    }

}
