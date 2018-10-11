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
package org.eclipse.ditto.services.base.actors;

import java.time.Duration;
import java.util.Collections;

import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.signals.commands.devops.namespace.BlockNamespace;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which subscribes to {@link org.eclipse.ditto.signals.commands.devops.namespace.BlockNamespace} via Akka
 * Pub-Sub and writes the caches the blocked namespaces in a {@link org.eclipse.ditto.services.utils.cache.Cache}.
 */
public final class BlockedNamespaceCacheActor extends AbstractPubSubListenerActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "blockedNamespaceCacheActor";

    private final Cache<String, Object> namespaceCache;

    private BlockedNamespaceCacheActor(final Cache<String, Object> namespaceCache,
            final ActorRef pubSubMediator,
            final int instanceIndex) {
        super(pubSubMediator, Collections.singleton(BlockNamespace.TYPE), instanceIndex);
        this.namespaceCache = namespaceCache;
    }

    /**
     * Creates {@code Props} for this Actor.
     *
     * @param namespaceCache the cache for blocked namespaces.
     * @param pubSubMediator the akka pub-sub mediator.
     * @param instanceIndex the instance index.
     * @return the Props.
     */
    public static Props props(final Cache<String, Object> namespaceCache, final ActorRef pubSubMediator,
            final int instanceIndex) {
        return Props.create(BlockedNamespaceCacheActor.class,
                () -> new BlockedNamespaceCacheActor(namespaceCache, pubSubMediator, instanceIndex));
    }

    /**
     * Create a new cache for namespace blocking.
     *
     * @param timeToBlock the duration for which a namespace should be blocked.
     * @return a new cache.
     */
    public static Cache<String, Object> newCache(final Duration timeToBlock) {
        return CaffeineCache.of(Caffeine.newBuilder().expireAfterWrite(timeToBlock));
    }

    @Override
    protected Receive handleEvents() {
        return ReceiveBuilder.create()
                .match(BlockNamespace.class, this::blockNamespace)
                .build();
    }

    private void blockNamespace(final BlockNamespace blockNamespace) {
        final String namespace = blockNamespace.getNamespace();
        namespaceCache.put(namespace, namespace);
    }

}
