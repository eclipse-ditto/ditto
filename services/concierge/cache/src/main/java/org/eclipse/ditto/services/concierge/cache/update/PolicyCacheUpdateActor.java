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
package org.eclipse.ditto.services.concierge.cache.update;

import static java.util.Objects.requireNonNull;

import java.util.Collections;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * An actor which subscribes to Policy Events and updates caches when necessary.
 */
public class PolicyCacheUpdateActor extends AbstractPubSubListenerActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "policyCacheUpdater";

    private final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache;

    private PolicyCacheUpdateActor(final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final ActorRef pubSubMediator, final int instanceIndex) {

        super(pubSubMediator, Collections.singleton(PolicyEvent.TYPE_PREFIX), instanceIndex);

        this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
    }

    /**
     * Create an Akka {@code Props} object for this actor.
     *
     * @param policyEnforcerCache the policy-enforcer cache.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param instanceIndex the index of this service instance.
     * @return Akka {@code Props} object.
     */
    public static Props props(final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final ActorRef pubSubMediator, final int instanceIndex) {
        requireNonNull(policyEnforcerCache);
        requireNonNull(pubSubMediator);

        return Props.create(PolicyCacheUpdateActor.class,
                () -> new PolicyCacheUpdateActor(policyEnforcerCache, pubSubMediator, instanceIndex));
    }

    @Override
    protected Receive handleEvents() {
        return receiveBuilder().match(PolicyEvent.class, this::handleEvent).build();
    }

    private void handleEvent(final PolicyEvent policyEvent) {
        final EntityId key = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyEvent.getId());
        policyEnforcerCache.invalidate(key);
    }
}
