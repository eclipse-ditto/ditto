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
package org.eclipse.ditto.services.concierge.enforcement;

import java.time.Duration;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.concierge.common.ConciergeConfig;
import org.eclipse.ditto.services.concierge.common.DittoConciergeConfig;
import org.eclipse.ditto.services.concierge.common.EnforcementConfig;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CacheKey;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Sink;

/**
 * Extensible actor to execute enforcement behavior.
 */
public abstract class AbstractEnforcerActor extends AbstractGraphActor<Contextual<WithDittoHeaders>,
        WithDittoHeaders> {

    private static final String TIMER_NAME = "concierge_enforcements";

    /**
     * Contextual information about this actor.
     */
    protected final Contextual<WithDittoHeaders> contextual;

    private final EnforcementConfig enforcementConfig;

    @Nullable
    private final Cache<CacheKey, Entry<CacheKey>> thingIdCache;
    @Nullable
    private final Cache<CacheKey, Entry<Enforcer>> policyEnforcerCache;


    /**
     * Create an instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @param conciergeForwarder the concierge forwarder.
     * @param thingIdCache the cache for Thing IDs to Policy ID.
     * @param policyEnforcerCache the Policy cache.
     */
    protected AbstractEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            @Nullable final Cache<CacheKey, Entry<CacheKey>> thingIdCache,
            @Nullable final Cache<CacheKey, Entry<Enforcer>> policyEnforcerCache) {

        super(WithDittoHeaders.class);

        final ConciergeConfig conciergeConfig = DittoConciergeConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        enforcementConfig = conciergeConfig.getEnforcementConfig();

        this.thingIdCache = thingIdCache;
        this.policyEnforcerCache = policyEnforcerCache;

        contextual = Contextual.forActor(getSelf(), getContext().getSystem().deadLetters(),
                pubSubMediator, conciergeForwarder, enforcementConfig.getAskTimeout(), logger,
                createResponseReceiverCache(conciergeConfig)
        );

        // register for sending messages via pub/sub to this enforcer
        // used for receiving cache invalidations from brother concierge nodes
        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());
        // register for receiving invalidate policy enforcers
        pubSubMediator.tell(DistPubSubAccess.subscribe(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, self()),
                ActorRef.noSender());
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(PolicyTag.class, policyTag -> {
                    logger.debug("Received <{}> -> Invalidating caches...", policyTag);
                    final CacheKey entityId = CacheKey.of(policyTag.getEntityId());
                    invalidateCaches(entityId);
                })
                .match(InvalidateCacheEntry.class, invalidateCacheEntry -> {
                    logger.debug("Received <{}> -> Invalidating caches...", invalidateCacheEntry);
                    final CacheKey entityId = invalidateCacheEntry.getEntityId();
                    invalidateCaches(entityId);
                });
    }

    private void invalidateCaches(final CacheKey entityId) {
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
    protected Contextual<WithDittoHeaders> beforeProcessMessage(final Contextual<WithDittoHeaders> contextual) {
        return contextual.withTimer(createTimer(contextual.getMessage()));
    }

    private StartedTimer createTimer(final WithDittoHeaders withDittoHeaders) {
        final PreparedTimer expiringTimer = DittoMetrics.timer(TIMER_NAME);

        withDittoHeaders.getDittoHeaders().getChannel().ifPresent(channel ->
                expiringTimer.tag("channel", channel)
        );
        if (withDittoHeaders instanceof Signal) {
            expiringTimer.tag("resource", ((Signal<?>) withDittoHeaders).getResourceType());
        }
        if (withDittoHeaders instanceof Command) {
            expiringTimer.tag("category", ((Command<?>) withDittoHeaders).getCategory().name().toLowerCase());
        }
        return expiringTimer.start();
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

    @Nullable
    private static Cache<String, ActorRef> createResponseReceiverCache(final ConciergeConfig conciergeConfig) {
        if (conciergeConfig.getEnforcementConfig().shouldDispatchLiveResponsesGlobally()) {
            return CaffeineCache.of(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(120L)));
        } else {
            return null;
        }
    }

}
