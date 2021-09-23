/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.starter.actors;

import java.time.Duration;
import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.namespaces.NamespaceReader;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.Replicator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that invalidates all entries of all caches belonging to blocked namespaces.
 */
public final class CachedNamespaceInvalidator extends AbstractActorWithTimers {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "cachedNamespaceInvalidator";

    /**
     * Name of the dispatcher of this actor. It should be different from the actor system's default dispatcher
     * so that this actor may safely block.
     */
    private static final String DISPATCHER_NAME = "cached-namespace-invalidator-dispatcher";

    /**
     * Delay to not race with messages in-flight.
     */
    private static final Duration INVALIDATION_DELAY = Duration.ofSeconds(5L);

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Collection<Cache<EnforcementCacheKey, ?>> cachesToMaintain;

    @SuppressWarnings("unused")
    private CachedNamespaceInvalidator(final BlockedNamespaces blockedNamespaces,
            final Collection<Cache<EnforcementCacheKey, ?>> cachesToMaintain) {

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
    public static Props props(final BlockedNamespaces blocked,
            final Collection<Cache<EnforcementCacheKey, ?>> caches) {
        return Props.create(CachedNamespaceInvalidator.class, blocked, caches)
                .withDispatcher(DISPATCHER_NAME);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Replicator.Changed.class, this::handleChanged)
                .match(InvalidateCachedNamespaces.class, this::invalidateCachedNamespaces)
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
            logNamespaces("Received", namespaces);
            invalidateNamespacesAfterDelay(namespaces);
        } else {
            logUnhandled(changed);
        }
    }

    private void logNamespaces(final String verb, final ORSet<String> namespaces) {
        if (namespaces.size() > 25) {
            log.info("{} <{}> namespaces", verb, namespaces.size());
        } else {
            log.info("{} namespaces: <{}>", verb, namespaces);
        }
    }

    private void invalidateNamespacesAfterDelay(final ORSet<String> namespaces) {
        getTimers().startSingleTimer(namespaces, new InvalidateCachedNamespaces(namespaces), INVALIDATION_DELAY);
    }

    private void invalidateCachedNamespaces(final InvalidateCachedNamespaces invalidate) {
        logNamespaces("Invalidating", invalidate.namespaces);
        cachesToMaintain.forEach(cache -> invalidateNamespaces(cache, invalidate.namespaces));
    }

    private void invalidateNamespaces(final Cache<EnforcementCacheKey, ?> cache, final ORSet<String> namespaces) {
        if (!namespaces.isEmpty()) {
            final Collection<EnforcementCacheKey> keysToInvalidate = cache.asMap()
                    .keySet()
                    .stream()
                    .filter(entityId -> containsNamespaceOfEntityId(namespaces, entityId))
                    .collect(Collectors.toList());

            cache.invalidateAll(keysToInvalidate);
        }
    }

    private static boolean containsNamespaceOfEntityId(final ORSet<String> namespaces,
            final EnforcementCacheKey entityId) {
        return NamespaceReader.fromEntityId(entityId.getId())
                .map(namespaces::contains)
                .orElse(false);
    }

    private static final class InvalidateCachedNamespaces {

        final ORSet<String> namespaces;

        private InvalidateCachedNamespaces(final ORSet<String> namespaces) {
            this.namespaces = namespaces;
        }
    }
}
