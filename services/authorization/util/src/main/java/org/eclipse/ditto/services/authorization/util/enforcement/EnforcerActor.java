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
package org.eclipse.ditto.services.authorization.util.enforcement;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that enforces authorization for all commands.
 */
public final class EnforcerActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final EntityRegionMap entityRegionMap;
    private final AuthorizationCaches caches;
    private final EntityId entityId;

    private final PolicyCommandEnforcement policyCommandEnforcement;
    private final ThingCommandEnforcement thingCommandEnforcement;

    private EnforcerActor(final ActorRef pubSubMediator,
            final EntityRegionMap entityRegionMap,
            final AuthorizationCaches caches) {
        this.pubSubMediator = pubSubMediator;
        this.entityRegionMap = entityRegionMap;
        this.caches = caches;
        this.entityId = decodeEntityId(getSelf());

        final Enforcement.Data data = new Enforcement.Data(
                Duration.ofSeconds(10), // TODO: make configurable
                entityRegionMap,
                entityId,
                log,
                caches,
                getSelf(),
                getContext().getSystem().deadLetters());

        policyCommandEnforcement = new PolicyCommandEnforcement(data);
        thingCommandEnforcement = new ThingCommandEnforcement(data, policyCommandEnforcement);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param entityRegionMap map from resource types to entity shard regions.
     * @param authorizationCaches cache of information relevant for authorization.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final EntityRegionMap entityRegionMap,
            final AuthorizationCaches authorizationCaches) {

        return Props.create(EnforcerActor.class,
                () -> new EnforcerActor(pubSubMediator, entityRegionMap, authorizationCaches));
    }

    @Override
    public void postStop() {
        // if stopped, remove self from entity ID cache.
        caches.invalidateEntityId(entityId);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ThingCommand.class, cmd -> thingCommandEnforcement.enforceThingCommand(cmd, getSender()))
                .matchAny(message -> {
                    log.warning("Unexpected message: <{}>", message);
                    unhandled(message);
                })
                .build();
    }

    private static EntityId decodeEntityId(final ActorRef self) {
        final String name = self.path().name();
        try {
            final String typeWithPath = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
            return EntityId.readFrom(typeWithPath);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }
    }
}
