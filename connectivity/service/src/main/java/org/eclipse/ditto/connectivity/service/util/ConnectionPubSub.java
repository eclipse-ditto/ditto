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

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.DistributedSub;
import org.eclipse.ditto.internal.utils.pubsub.api.SubAck;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Pub-sub for messages between client actors of a connection.
 */
public final class ConnectionPubSub implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    private final DistributedPub<Signal<?>> pub;
    private final DistributedSub sub;

    private ConnectionPubSub(final DistributedPub<Signal<?>> pub, final DistributedSub sub) {
        this.pub = pub;
        this.sub = sub;
    }

    /**
     * Look up the connection pub sub extension.
     *
     * @param system the actor system.
     * @return the connection pub sub extension of the actor system.
     */
    public static ConnectionPubSub get(final ActorSystem system) {
        return EXTENSION_ID.get(system);
    }

    /**
     * Publish a search signal for the responsible client actor.
     *
     * @param signal the signal.
     * @param connectionId the connection ID.
     * @param groupIndexKey the group index key to choose the responsible client actor, either the entity ID or the
     * subscription ID prefix.
     * @param sender the sender of the signal.
     */
    public void publishSignal(final Signal<?> signal, final ConnectionId connectionId, final CharSequence groupIndexKey,
            @Nullable final ActorRef sender) {
        pub.publish(setConnectionId(signal, connectionId), groupIndexKey, sender);
    }

    /**
     * Subscribe as a client actor.
     *
     * @param connectionId connection ID of the client actor.
     * @param clientActor the client actor.
     * @param resubscribe whether this is a resubscription.
     * @return a future that completes with the consistency check result if it is a resubscription or {@code true}
     * otherwise.
     */
    public CompletionStage<Boolean> subscribe(final ConnectionId connectionId, final ActorRef clientActor,
            final boolean resubscribe) {
        final var idString = connectionId.toString();
        return sub.subscribeWithFilterAndGroup(List.of(idString), clientActor, null, idString, resubscribe)
                .thenApply(SubAck::isConsistent);
    }

    /**
     * Unsubscribe as a client actor.
     *
     * @param connectionId connection ID of the client actor.
     * @param clientActor the client actor.
     * @return a future that completes or fails according to whether unsubscription is successful.
     * otherwise.
     */
    public CompletionStage<Void> unsubscribe(final ConnectionId connectionId, final ActorRef clientActor) {
        final var idString = connectionId.toString();
        return sub.unsubscribeWithAck(List.of(idString), clientActor).thenApply(unsubAck -> null);
    }

    private static <T extends DittoHeadersSettable<T>> T setConnectionId(final DittoHeadersSettable<T> signal,
            final ConnectionId connectionId) {
        return signal.setDittoHeaders(
                signal.getDittoHeaders()
                        .toBuilder()
                        .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId)
                        .build()
        );
    }

    private static final class ExtensionId extends AbstractExtensionId<ConnectionPubSub> {

        private ExtensionId() {}

        @Override
        public ConnectionPubSub createExtension(final ExtendedActorSystem system) {
            final var factory = ConnectionPubSubFactory.of(system);
            return new ConnectionPubSub(factory.startDistributedPub(), factory.startDistributedSub());
        }
    }

}
