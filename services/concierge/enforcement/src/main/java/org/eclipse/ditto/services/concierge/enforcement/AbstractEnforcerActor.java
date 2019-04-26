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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.entry.Entry;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Flow;

/**
 * Extensible actor to execute enforcement behavior.
 */
public abstract class AbstractEnforcerActor extends AbstractGraphActor<Contextual<WithDittoHeaders>> {

    /**
     * Contextual information about this actor.
     */
    protected final Contextual<WithDittoHeaders> contextual;

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
     * @param enforcerExecutor executor for enforcement steps.
     * @param askTimeout how long to wait for entity actors.
     * @param bufferSize the buffer size used for the Source queue.
     * @param parallelism parallelism to use for processing messages in parallel.
     * @param thingIdCache the cache for Thing IDs to either ACL or Policy ID.
     * @param aclEnforcerCache the ACL cache.
     * @param policyEnforcerCache the Policy cache.
     */
    protected AbstractEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            final Duration askTimeout,
            final int bufferSize,
            final int parallelism,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        super(bufferSize, parallelism);

        this.thingIdCache = thingIdCache;
        this.aclEnforcerCache = aclEnforcerCache;
        this.policyEnforcerCache = policyEnforcerCache;

        contextual = new Contextual<>(null, getSelf(), getContext().getSystem().deadLetters(),
                pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout, log, null,
                createResponseReceiversCache());

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
    protected abstract Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> getHandler();

    @Override
    protected Contextual<WithDittoHeaders> mapMessage(final WithDittoHeaders<?> message) {
        return contextual.withReceivedMessage(message, getSender());
    }

    private static Cache<String, ActorRef> createResponseReceiversCache() {
        return CaffeineCache.of(Caffeine.newBuilder().expireAfterWrite(120, TimeUnit.SECONDS));
    }
}
