/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnectionResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.slf4j.Logger;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;

public final class DefaultUpdatedConnectionTester implements UpdatedConnectionTester {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(UpdatedConnectionTester.class);
    private final ActorRef connectionShardRegion;

    public DefaultUpdatedConnectionTester(final ActorSystem actorSystem) {
        final var clusterConfig =
                DefaultClusterConfig.of(actorSystem.settings().config().getConfig("ditto.cluster"));
        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);
        connectionShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                ConnectivityMessagingConstants.CLUSTER_ROLE, ConnectivityMessagingConstants.SHARD_REGION);
    }

    @Override
    public CompletionStage<Optional<TestConnectionResponse>> testConnection(final Connection updatedConnection,
            final DittoHeaders dittoHeaders) {
        final var dittoSudoHeaders = dittoHeaders.toBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build();
        /*
         New ID is required to make sure that the test connection command can be properly applied,
          because it's only supported for not yet existing connections.
         */
        final Connection updatedConnectionWithNewID = updatedConnection.toBuilder()
                .id(ConnectionId.generateRandom())
                .build();
        final var testConnection = TestConnection.of(updatedConnectionWithNewID, dittoSudoHeaders);
        return Patterns.ask(connectionShardRegion, testConnection, Duration.ofSeconds(60))
                .handle((response, error) -> {
                    if (response instanceof TestConnectionResponse testConnectionResponse) {
                        LOGGER.info("got TestConnectionResponse <{}>.", response);
                        return Optional.of(testConnectionResponse);
                    } else if (response instanceof DittoRuntimeException || response instanceof ErrorResponse<?>) {
                        LOGGER.info("Got error when testing connection <{}>: <{}>", updatedConnection.getId(),
                                response);
                        return Optional.empty();
                    } else if (response != null) {
                        LOGGER.info("got unexpected response <{}>", response);
                        return Optional.empty();
                    } else if (error != null) {
                        LOGGER.info("Got error when testing connection <{}>", updatedConnection.getId(), error);
                        return Optional.empty();
                    } else {
                        LOGGER.warn("Got neither a response nor an error. This is unexpected.");
                        return Optional.empty();
                    }
                });
    }

}
