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
package org.eclipse.ditto.services.models.concierge;

import static akka.cluster.pubsub.DistributedPubSubMediator.Send;
import static org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants.DISPATCHER_ACTOR_PATH;

import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;

/**
 * Wrap messages and forward them to the concierge service.
 */
public final class ConciergeForwarder {

    private final ActorRef pubSubMediator;
    private final ActorRef enforcerShardRegion;

    private static final Logger LOGGER = LoggerFactory.getLogger(ConciergeForwarder.class);

    /**
     * Create an object to forward messages to concierge service.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param enforcerShardRegion shard region of enforcer actors.
     */
    public ConciergeForwarder(final ActorRef pubSubMediator, final ActorRef enforcerShardRegion) {
        this.pubSubMediator = pubSubMediator;
        this.enforcerShardRegion = enforcerShardRegion;
    }

    /**
     * Forwards the passed {@code signal} based on whether it has an entity ID or not to the {@code pubSubMediator}
     * or the {@code enforcerShardRegion}.
     *
     * @param signal the Signal to forward
     * @param sender the ActorRef to use as sender
     */
    public void forward(final Signal<?> signal, final ActorRef sender) {
        if (signal.getId().isEmpty()) {
            LOGGER.debug("Signal does not contain ID, forwarding to concierge-dispatcherActor: <{}>.", signal);
            final Send msg = wrapForPubSub(signal);
            LOGGER.debug("Sending message to concierge-dispatcherActor: <{}>.", msg);
            pubSubMediator.tell(msg, sender);
        } else {
            LOGGER.debug("Signal has ID <{}>, forwarding to concierge-shard-region: <{}>.",
                    signal.getId(), signal);
            final ShardedMessageEnvelope msg = wrapForEnforcer(signal);
            LOGGER.debug("Sending message to concierge-shard-region: <{}>.", msg);
            enforcerShardRegion.tell(msg, sender);
        }
    }

    /**
     * Wrap a signal in a sharded message envelope addressed to the correct {@code EnforcerActor}.
     *
     * @param signal the signal to wrap.
     * @return the sharded message envelope.
     */
    public static ShardedMessageEnvelope wrapForEnforcer(final Signal<?> signal) {
        final EntityId entityId;
        if (signal instanceof MessageCommand) {
            entityId = EntityId.of(ThingCommand.RESOURCE_TYPE, signal.getId());
        } else {
            entityId = EntityId.of(signal.getResourceType(), signal.getId());
        }
        return createEnvelope(entityId, signal);
    }

    private static Send wrapForPubSub(final Signal<?> signal) {
        return new Send(DISPATCHER_ACTOR_PATH, signal);
    }

    private static ShardedMessageEnvelope createEnvelope(final EntityId entityId, final Signal<?> signal) {
        return ShardedMessageEnvelope.of(
                entityId.toString(),
                signal.getType(),
                signal.toJson(signal.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                signal.getDittoHeaders());
    }
}
