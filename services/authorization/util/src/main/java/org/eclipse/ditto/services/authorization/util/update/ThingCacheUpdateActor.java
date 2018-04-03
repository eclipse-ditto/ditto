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
package org.eclipse.ditto.services.authorization.util.update;

import static java.util.Objects.requireNonNull;

import java.util.Collections;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * An actor which subscribes to Thing Events and updates caches when necessary.
 */
public class ThingCacheUpdateActor extends PubSubListenerActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "thingCacheUpdater";

    private final Cache<EntityId, Entry<Enforcer>> enforcerCache;
    private final Cache<EntityId, Entry<EntityId>> thingIdCache;

    /**
     * Constructor.
     *
     * @param enforcerCache the enforcer cache.
     * @param thingIdCache the thing-id-cache.
     * @param pubSubMediator the DistributedPubSubMediator for registering for events.
     * @param instanceIndex index of this service instance.
     */
    public ThingCacheUpdateActor(final Cache<EntityId, Entry<Enforcer>> enforcerCache,
            final Cache<EntityId, Entry<EntityId>> thingIdCache, final ActorRef pubSubMediator,
            final int instanceIndex) {

        super(pubSubMediator, Collections.singleton(ThingEvent.TYPE_PREFIX), instanceIndex);

        this.enforcerCache = requireNonNull(enforcerCache);
        this.thingIdCache = requireNonNull(thingIdCache);
    }

    /**
     * Create an Akka {@code Props} object for this actor.
     *
     * @param enforcerCache the enforcer cache.
     * @param thingIdCache the thing-id cache.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param instanceIndex the index of this service instance.
     * @return Akka {@code Props} object.
     */
    public static Props props(final Cache<EntityId, Entry<Enforcer>> enforcerCache,
            final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final ActorRef pubSubMediator, final int instanceIndex) {
        requireNonNull(enforcerCache);
        requireNonNull(pubSubMediator);

        return Props.create(ThingCacheUpdateActor.class,
                () -> new ThingCacheUpdateActor(enforcerCache, thingIdCache, pubSubMediator, instanceIndex));
    }

    @Override
    protected Receive handleEvents() {
        return receiveBuilder().match(ThingEvent.class, this::handleEvent).build();
    }

    public void handleEvent(final ThingEvent thingEvent) {
        // TODO CR-5397: be less wasteful.
        final EntityId key = EntityId.of(ThingCommand.RESOURCE_TYPE, thingEvent.getThingId());

        thingIdCache.invalidate(key);

        // invalidate acl enforcers
        enforcerCache.invalidate(key);
    }
}
