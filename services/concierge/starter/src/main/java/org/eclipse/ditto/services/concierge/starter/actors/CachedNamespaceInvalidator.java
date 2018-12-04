/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.starter.actors;

import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.services.models.caching.EntityId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.Replicator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that invalidates all entries of all caches belonging to blocked namespaces.
 */
public final class CachedNamespaceInvalidator extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "cachedNamespaceInvalidator";

    /**
     * Name of the dispatcher of this actor. It should be different from the actor system's default dispatcher
     * so that this actor may safely block.
     */
    private static final String DISPATCHER_NAME = "cached-namespace-invalidator-dispatcher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Collection<Cache<EntityId, ?>> cachesToMaintain;

    private CachedNamespaceInvalidator(final BlockedNamespaces blockedNamespaces,
            final Collection<Cache<EntityId, ?>> cachesToMaintain) {

        blockedNamespaces.subscribeForChanges(getSelf());
        this.cachesToMaintain = cachesToMaintain;
    }

    /**
     * Create Props of an actor to invalidate cache entries in blocked namespaces.
     *
     * @param blocked distributed set of blocked namespaces.
     * @param caches caches to invalidate.
     * @return the Props object.
     */
    public static Props props(final BlockedNamespaces blocked, final Collection<Cache<EntityId, ?>> caches) {
        return Props.create(CachedNamespaceInvalidator.class, () -> new CachedNamespaceInvalidator(blocked, caches))
                .withDispatcher(DISPATCHER_NAME);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Replicator.Changed.class, this::handleChanged)
                .matchAny(this::logUnhandled)
                .build();
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    @SuppressWarnings("unchecked")
    private void handleChanged(final Replicator.Changed<?> changed) {
        if (changed.dataValue() instanceof ORSet) {
            final ORSet<String> namespaces = (ORSet<String>) changed.dataValue();
            log.info("Invalidating <{}> namespaces", namespaces.size());
            invalidateCachedNamespaces(namespaces);
        } else {
            logUnhandled(changed);
        }
    }

    private void invalidateCachedNamespaces(final ORSet<String> namespaces) {
        cachesToMaintain.forEach(cache -> invalidateNamespaces(cache, namespaces));
    }

    private void invalidateNamespaces(final Cache<EntityId, ?> cache, final ORSet<String> namespaces) {
        if (!namespaces.isEmpty()) {
            final Collection<EntityId> keysToInvalidate = cache.asMap()
                    .keySet()
                    .stream()
                    .filter(entityId -> containsNamespaceOfEntityId(namespaces, entityId))
                    .collect(Collectors.toList());

            cache.invalidateAll(keysToInvalidate);
        }
    }

    private static boolean containsNamespaceOfEntityId(final ORSet<String> namespaces, final EntityId entityId) {
        return NamespaceReader.fromEntityId(entityId.getId())
                .map(namespaces::contains)
                .orElse(false);
    }
}
