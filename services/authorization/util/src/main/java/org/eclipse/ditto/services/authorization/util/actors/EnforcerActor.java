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

import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCache;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that enforces authorization for all commands.
 */
public class EnforcerActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final EntityRegionMap entityRegionMap;
    private final AuthorizationCache authorizationCache;
    private final ResourceKey cacheKey;

    private EnforcerActor(final ActorRef pubSubMediator,
            final EntityRegionMap entityRegionMap,
            final AuthorizationCache authorizationCache) {
        this.pubSubMediator = pubSubMediator;
        this.entityRegionMap = entityRegionMap;
        this.authorizationCache = authorizationCache;
        this.cacheKey = getCacheKey(getSelf());
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param entityRegionMap map from resource types to entity shard regions.
     * @param authorizationCache cache of information relevant for authorization.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final EntityRegionMap entityRegionMap,
            final AuthorizationCache authorizationCache) {

        return Props.create(EnforcerActor.class,
                () -> new EnforcerActor(pubSubMediator, entityRegionMap, authorizationCache));
    }

    @Override
    public void postStop() {
        // if stopped, remove self from entity ID cache.
        authorizationCache.invalidateEntityId(cacheKey);
    }

    @Override
    public Receive createReceive() {
        // TODO: do something
        return ReceiveBuilder.create()
                .matchAny(message -> {
                    log.warning("unexpected message: <{}>", message);
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
