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

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.concierge.common.DittoConciergeConfig;
import org.eclipse.ditto.services.concierge.common.EnforcementConfig;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.ExpiringTimerBuilder;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * Extensible actor to execute enforcement behavior.
 */
public abstract class AbstractEnforcerActor extends AbstractGraphActor<Contextual<WithDittoHeaders>> {

    private static final String TIMER_NAME = "concierge_enforcements";

    /**
     * Contextual information about this actor.
     */
    protected final Contextual<WithDittoHeaders> contextual;

    private final EnforcementConfig enforcementConfig;

    @Nullable
    private final Cache<EntityId, Entry<EntityId>> thingIdCache;
    @Nullable
    private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;
    @Nullable
    private final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache;


    /**
     * Create an instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @param conciergeForwarder the concierge forwarder.
     * @param thingIdCache the cache for Thing IDs to either ACL or Policy ID.
     * @param aclEnforcerCache the ACL cache.
     * @param policyEnforcerCache the Policy cache.
     */
    protected AbstractEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        super();

        enforcementConfig = DittoConciergeConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getEnforcementConfig();

        this.thingIdCache = thingIdCache;
        this.aclEnforcerCache = aclEnforcerCache;
        this.policyEnforcerCache = policyEnforcerCache;

        contextual = new Contextual<>(null, getSelf(), getContext().getSystem().deadLetters(),
                pubSubMediator, conciergeForwarder, enforcementConfig.getAskTimeout(), log, null, null,
                null, null, createResponseReceiversCache());

        // register for sending messages via pub/sub to this enforcer
        // used for receiving cache invalidations from brother concierge nodes
        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(InvalidateCacheEntry.class, invalidateCacheEntry -> {
            log.debug("received <{}>", invalidateCacheEntry);
            final EntityId entityId = invalidateCacheEntry.getEntityId();
            invalidateCaches(entityId);
        });
    }

    private void invalidateCaches(final EntityId entityId) {
        if (thingIdCache != null) {
            final boolean invalidated = thingIdCache.invalidate(entityId);
            log.debug("thingId cache for entity id <{}> was invalidated: {}", entityId, invalidated);
        }
        if (aclEnforcerCache != null) {
            final boolean invalidated = aclEnforcerCache.invalidate(entityId);
            log.debug("acl enforcer cache for entity id <{}> was invalidated: {}", entityId, invalidated);
        }
        if (policyEnforcerCache != null) {
            final boolean invalidated = policyEnforcerCache.invalidate(entityId);
            log.debug("policy enforcer cache for entity id <{}> was invalidated: {}", entityId, invalidated);
        }
    }

    @Override
    protected Contextual<WithDittoHeaders> beforeProcessMessage(final Contextual<WithDittoHeaders> contextual) {
        return contextual.withTimer(createTimer(contextual.getMessage()));
    }

    private StartedTimer createTimer(final WithDittoHeaders withDittoHeaders) {
        final ExpiringTimerBuilder timerBuilder = DittoMetrics.expiringTimer(TIMER_NAME);

        withDittoHeaders.getDittoHeaders().getChannel().ifPresent(channel ->
                timerBuilder.tag("channel", channel)
        );
        if (withDittoHeaders instanceof Signal) {
            timerBuilder.tag("resource", ((Signal) withDittoHeaders).getResourceType());
        }
        if (withDittoHeaders instanceof Command) {
            timerBuilder.tag("category", ((Command) withDittoHeaders).getCategory().name().toLowerCase());
        }
        return timerBuilder.build();
    }

    @Override
    protected abstract Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> processMessageFlow();

    @Override
    protected abstract Sink<Contextual<WithDittoHeaders>, ?> processedMessageSink();

    @Override
    protected int getBufferSize() {
        return enforcementConfig.getBufferSize();
    }

    @Override
    protected int getParallelism() {
        return enforcementConfig.getParallelism();
    }

    @Override
    protected int getMaxNamespacesSubstreams() {
        return enforcementConfig.getMaxNamespacesSubstreams();
    }

    @Override
    protected Contextual<WithDittoHeaders> mapMessage(final WithDittoHeaders message) {
        return contextual.withReceivedMessage(message, getSender());
    }

    private static Cache<String, ActorRef> createResponseReceiversCache() {
        return CaffeineCache.of(Caffeine.newBuilder().expireAfterWrite(120, TimeUnit.SECONDS));
    }
}
