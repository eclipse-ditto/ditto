/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheKey;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Sink;

/**
 * Extensible actor to execute enforcement behavior.
 * TODO TJ candidate for removal
 */
public abstract class AbstractEnforcerActor extends AbstractGraphActor<Contextual<WithDittoHeaders>,
        WithDittoHeaders> {

    /**
     * Contextual information about this actor.
     */
    protected final Contextual<WithDittoHeaders> contextual;

    private final EnforcementConfig enforcementConfig;

    @Nullable private final Cache<CacheKey, Entry<CacheKey>> thingIdCache;
    @Nullable private final Cache<CacheKey, Entry<Enforcer>> policyEnforcerCache;


    /**
     * Create an instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @param conciergeForwarder the concierge forwarder.
     * @param enforcementConfig TODO TJ
     * @param thingIdCache the cache for Thing IDs to Policy ID.
     * @param policyEnforcerCache the Policy cache.
     */
    protected AbstractEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final EnforcementConfig enforcementConfig,
            @Nullable final Cache<CacheKey, Entry<CacheKey>> thingIdCache,
            @Nullable final Cache<CacheKey, Entry<Enforcer>> policyEnforcerCache) {

        super(WithDittoHeaders.class, UnaryOperator.identity());

        final var actorContext = getContext();
        final var actorSystem = actorContext.getSystem();
        this.enforcementConfig = checkNotNull(enforcementConfig, "enforcementConfig");

        this.thingIdCache = thingIdCache;
        this.policyEnforcerCache = policyEnforcerCache;

        final var self = getSelf();
        contextual = Contextual.forActor(self,
                actorSystem,
                pubSubMediator,
                conciergeForwarder,
                enforcementConfig.getAskWithRetryConfig(),
                logger);

        // register for sending messages via pub/sub to this enforcer
        // used for receiving cache invalidations from brother concierge nodes
        pubSubMediator.tell(DistPubSubAccess.put(self), self);
        // register for receiving invalidate policy enforcers
        pubSubMediator.tell(DistPubSubAccess.subscribe(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, self),
                ActorRef.noSender());
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(PolicyTag.class, policyTag -> {
                    logger.debug("Received <{}> -> Invalidating caches...", policyTag);
                    final var entityId = EnforcementCacheKey.of(policyTag.getEntityId());
                    invalidateCaches(entityId);
                })
                .match(InvalidateCacheEntry.class, invalidateCacheEntry -> {
                    logger.debug("Received <{}> -> Invalidating caches...", invalidateCacheEntry);
                    final EnforcementCacheKey entityId = invalidateCacheEntry.getEntityId();
                    invalidateCaches(entityId);
                });
    }

    private void invalidateCaches(final EnforcementCacheKey entityId) {
        if (thingIdCache != null) {
            final boolean invalidated = thingIdCache.invalidate(entityId);
            logger.debug("Thing ID cache for entity ID <{}> was invalidated: {}", entityId, invalidated);
        }
        if (policyEnforcerCache != null) {
            final boolean invalidated = policyEnforcerCache.invalidate(entityId);
            logger.debug("Policy enforcer cache for entity ID <{}> was invalidated: {}", entityId, invalidated);
        }
    }

    @Override
    protected abstract Sink<Contextual<WithDittoHeaders>, ?> createSink();

    @Override
    protected int getBufferSize() {
        return enforcementConfig.getBufferSize();
    }

    @Override
    protected Contextual<WithDittoHeaders> mapMessage(final WithDittoHeaders message) {
        return contextual.withReceivedMessage(message, getSender());
    }

}
