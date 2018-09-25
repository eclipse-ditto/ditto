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
package org.eclipse.ditto.services.base.actors;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.base.config.DevOpsConfigReader;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.signals.commands.devops.namespace.BlockNamespace;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * An actor which subscribes to DevOps namespace commands and writes the namespace cache.
 */
public class NamespaceCacheWriter extends AbstractPubSubListenerActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "namespaceCacheWriter";

    private final Cache<String, Object> namespaceCache;

    private NamespaceCacheWriter(final Cache<String, Object> namespaceCache,
            final ActorRef pubSubMediator,
            final int instanceIndex) {
        super(pubSubMediator, Collections.singleton(BlockNamespace.TYPE), instanceIndex);
        this.namespaceCache = namespaceCache;
    }

    public static Props props(final Cache<String, Object> namespaceCache,
            final ActorRef pubSubMediator,
            final int instanceIndex) {

        return Props.create(NamespaceCacheWriter.class,
                () -> new NamespaceCacheWriter(namespaceCache, pubSubMediator, instanceIndex));
    }

    /**
     * Create a new cache for namespace blocking.
     *
     * @param devOpsConfigReader config reader that governs namespace blocking.
     * @return a new cache.
     */
    public static Cache<String, Object> newCache(final DevOpsConfigReader devOpsConfigReader) {
        final Duration timeToBlock = devOpsConfigReader.namespaceBlockTime();
        return CaffeineCache.of(Caffeine.newBuilder().expireAfterWrite(timeToBlock));
    }

    /**
     * Create a pre-enforcer function from the cache that blocks all cached namespaces
     *
     * @param namespaceCache cache of namespaces.
     * @return the pre-enforcer function that raises an exception if and only if the namespace is cached.
     */
    public static Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> blockCachedNamespaces(
            final Cache<String, Object> namespaceCache,
            final BiFunction<String, WithDittoHeaders, DittoRuntimeException> errorCreator) {

        return NamespaceBlockingBehavior.of(namespaceCache, errorCreator).asPreEnforcer();
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
