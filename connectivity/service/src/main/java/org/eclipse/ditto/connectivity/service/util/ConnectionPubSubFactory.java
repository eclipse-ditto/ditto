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
package org.eclipse.ditto.connectivity.service.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.ConnectivityService;
import org.eclipse.ditto.internal.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;

/**
 * Pub-sub factory for messages between client actors of a connection.
 */
final class ConnectionPubSubFactory extends AbstractPubSubFactory<Signal<?>> {

    private static final AckExtractor<Signal<?>> ACK_EXTRACTOR =
            AckExtractor.of(ConnectionPubSubFactory::getConnectionId, Signal::getDittoHeaders);

    private static final DDataProvider PROVIDER = DDataProvider.of(ConnectivityService.SERVICE_NAME);

    @SuppressWarnings({"unchecked"})
    private ConnectionPubSubFactory(final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final PubSubTopicExtractor<Signal<?>> topicExtractor) {

        super(actorRefFactory, actorSystem, (Class<Signal<?>>) (Object) Signal.class, topicExtractor, PROVIDER,
                ACK_EXTRACTOR, DistributedAcks.empty(actorSystem));
    }

    /**
     * Create a pubsub factory for client actor messages.
     *
     * @param system the actor system.
     * @return the connection pub-sub factory.
     */
    public static ConnectionPubSubFactory of(final ActorSystem system) {
        return new ConnectionPubSubFactory(system, system, ConnectionPubSubFactory::getConnectionIdTopics);
    }

    private static String getConnectionIdString(final Signal<?> signal) {
        return Objects.requireNonNull(signal.getDittoHeaders().get(DittoHeaderDefinition.CONNECTION_ID.getKey()));
    }

    private static ConnectionId getConnectionId(final Signal<?> signal) {
        return ConnectionId.of(getConnectionIdString(signal));
    }

    private static Collection<String> getConnectionIdTopics(final Signal<?> signal) {
        return List.of(getConnectionIdString(signal));
    }
}
