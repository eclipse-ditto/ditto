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
package org.eclipse.ditto.services.authorization.util.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
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
public class EnforcerActor extends AbstractActor implements ThingCommandEnforcement {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final EntityRegionMap entityRegionMap;
    private final AuthorizationCaches caches;
    private final ResourceKey entityKey;

    private EnforcerActor(final ActorRef pubSubMediator,
            final EntityRegionMap entityRegionMap,
            final AuthorizationCaches caches) {
        this.pubSubMediator = pubSubMediator;
        this.entityRegionMap = entityRegionMap;
        this.caches = caches;
        this.entityKey = getCacheKey(getSelf());
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
        caches.invalidateEntityId(entityKey);
    }

    @Override
    public Duration getAskTimeout() {
        return Duration.ofSeconds(10L); // TODO: make configurable
    }

    @Override
    public EntityRegionMap entityRegionMap() {
        return entityRegionMap;
    }

    @Override
    public ResourceKey entityKey() {
        return entityKey;
    }

    @Override
    public DiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    public AuthorizationCaches caches() {
        return caches;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ThingCommand.class, this::enforceThingCommand)
                .matchAny(message -> {
                    log.warning("Unexpected message: <{}>", message);
                    unhandled(message);
                })
                .build();
    }

    private static ResourceKey getCacheKey(final ActorRef self) {
        final String name = self.path().name();
        try {
            final String typeWithPath = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
            return ResourceKey.newInstance(typeWithPath);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }
    }
}
