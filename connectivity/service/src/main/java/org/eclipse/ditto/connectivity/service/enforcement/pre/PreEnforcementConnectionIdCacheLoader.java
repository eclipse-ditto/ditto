/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.enforcement.pre;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.ActorAskCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.json.JsonFieldSelector;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Cache loader used for Connection existence check in pre-enforcement.
 */
final class PreEnforcementConnectionIdCacheLoader implements
        AsyncCacheLoader<ConnectionId, Entry<ConnectionId>> {

    private final ActorAskCacheLoader<ConnectionId, Command<?>, ConnectionId> delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param shardRegionProxy the shard-region-proxy.
     */
    public PreEnforcementConnectionIdCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final ActorRef shardRegionProxy) {

        delegate = ActorAskCacheLoader.forShard(askWithRetryConfig,
                scheduler,
                ConnectivityConstants.ENTITY_TYPE,
                shardRegionProxy,
                connectionId -> RetrieveConnection.of(connectionId, JsonFieldSelector.newInstance("id"), DittoHeaders.empty()),
                PreEnforcementConnectionIdCacheLoader::handleRetrieveConnectionResponse);
    }

    @Override
    public CompletableFuture<Entry<ConnectionId>> asyncLoad(final ConnectionId key, final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    private static Entry<ConnectionId> handleRetrieveConnectionResponse(final Object response) {

        if (response instanceof RetrieveConnectionResponse retrieveConnectionResponse) {
            final ConnectionId connectionId = retrieveConnectionResponse.getEntityId();
            return Entry.of(-1, connectionId);
        } else if (response instanceof ConnectionNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect RetrieveConnectionResponse, got: " + response);
        }
    }

}
