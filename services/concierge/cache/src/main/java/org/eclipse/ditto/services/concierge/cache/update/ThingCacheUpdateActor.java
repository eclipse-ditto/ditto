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
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * An actor which subscribes to Thing Events and updates caches when necessary.
 */
public class ThingCacheUpdateActor extends AbstractPubSubListenerActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "thingCacheUpdater";

    private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;
    private final Cache<EntityId, Entry<EntityId>> thingIdCache;

    private ThingCacheUpdateActor(final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            final Cache<EntityId, Entry<EntityId>> thingIdCache, final ActorRef pubSubMediator,
            final int instanceIndex) {

        super(pubSubMediator, Collections.singleton(ThingEvent.TYPE_PREFIX), instanceIndex);

        this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
        this.thingIdCache = requireNonNull(thingIdCache);
    }

    /**
     * Create an Akka {@code Props} object for this actor.
     *
     * @param aclEnforcerCache the acl-enforcer cache.
     * @param thingIdCache the thing-id cache.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param instanceIndex the index of this service instance.
     * @return Akka {@code Props} object.
     */
    public static Props props(final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final ActorRef pubSubMediator, final int instanceIndex) {
        requireNonNull(aclEnforcerCache);
        requireNonNull(thingIdCache);
        requireNonNull(pubSubMediator);

        return Props.create(ThingCacheUpdateActor.class,
                () -> new ThingCacheUpdateActor(aclEnforcerCache, thingIdCache, pubSubMediator, instanceIndex));
    }

    @Override
    protected Receive handleEvents() {
        return receiveBuilder().match(ThingEvent.class, this::handleEvent).build();
    }

    private void handleEvent(final ThingEvent event) {
        final EntityId key = EntityId.of(ThingCommand.RESOURCE_TYPE, event.getThingId());

        if (isAclAffected(event)) {
            aclEnforcerCache.invalidate(key);
        }

        if (isEnforcerIdAffected(event)) {
            thingIdCache.invalidate(key);
        }
    }

    private static boolean isEnforcerIdAffected(final ThingEvent event) {
        return isCompleteThingChanged(event) || isPolicyIdModified(event);
    }

    private static boolean isPolicyIdModified(final ThingEvent event) {
        // the policyId changes or is set initially (when converting a v1 to a v2 thing)
        return event instanceof PolicyIdModified;
    }

    private static boolean isAclAffected(final ThingEvent event) {
        return isCompleteThingChanged(event) || isAclChanged(event) || isPolicyIdModified(event);
    }

    private static boolean isAclChanged(final ThingEvent event) {
        return event instanceof AclModified || event instanceof AclEntryCreated || event instanceof AclEntryModified ||
                event instanceof AclEntryDeleted;
    }

    private static boolean isCompleteThingChanged(final ThingEvent event) {
        return event instanceof ThingCreated || event instanceof ThingModified || event instanceof ThingDeleted;
    }
}
