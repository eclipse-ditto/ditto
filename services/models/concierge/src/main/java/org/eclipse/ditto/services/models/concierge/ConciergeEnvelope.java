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
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.actor.ActorRef;

/**
 * Wrap messages and send them to concierge service.
 */
public final class ConciergeEnvelope {

    private final ActorRef pubSubMediator;
    private final ActorRef enforcerShardRegion;

    /**
     * Create an object to dispatch messages to concierge service.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param enforcerShardRegion shard region of enforcer actors.
     */
    public ConciergeEnvelope(final ActorRef pubSubMediator, final ActorRef enforcerShardRegion) {
        this.pubSubMediator = pubSubMediator;
        this.enforcerShardRegion = enforcerShardRegion;
    }

    public void dispatch(final Signal<?> signal, final ActorRef sender) {
        if (signal instanceof ThingSearchCommand || signal instanceof RetrieveThings) {
            pubSubMediator.tell(wrapForPubSub(signal), sender);
        } else {
            enforcerShardRegion.tell(wrapForEnforcer(signal), sender);
        }
    }

    /**
     * Wrap a signal in a sharded message envelope addressed to the correct {@code EnforcerActor}.
     *
     * @param signal the signal to wrap.
     * @return the sharded message envelope.
     */
    private static ShardedMessageEnvelope wrapForEnforcer(final Signal<?> signal) {
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
