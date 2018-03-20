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
package org.eclipse.ditto.services.authorization.util.actors;

import java.time.Duration;

import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Do NOT call the methods outside this package.
 */
public interface CommandEnforcementSelfType extends Actor {

    /**
     * Self-type requirement: It has the timeout duration for asking an entity shard region.
     * Do not call outside of this package.
     *
     * @return the timeout duration for asking an entity shard region.
     */
    Duration getAskTimeout();

    /**
     * Self-type requirement: It has an {@code EntityRegionMap}.
     * Do not call outside of this package.
     *
     * @return the entity region map.
     */
    EntityRegionMap entityRegionMap();

    /**
     * Self-type requirement: It has an entity ID.
     * Do not call outside of this package.
     *
     * @return the entity ID.
     */
    EntityId entityId();

    /**
     * Self-type requirement: It has a diagnostic logging adapter.
     * Do not call outside of this package.
     *
     * @return the diagnostic logging adapter.
     */
    DiagnosticLoggingAdapter log();

    /**
     * Self-type requirement: It has authorization caches.
     * Do not call outside of this package.
     *
     * @return the authorization caches.
     */
    AuthorizationCaches caches();

    /**
     * Method shared by {@code ThingCommandEnforcement} with other mixins: authorize a {@code CreateThing} command
     * containing an explicit policy ID sent to a nonexistent thing.
     *
     * @param createThing the command.
     * @param policyId policy ID contained in the command.
     * @param sender sender of the command.
     * @return 0 (must not be null).
     */
    boolean enforceCreateThingForNonexistentThingWithPolicyId(final CreateThing createThing,
            final String policyId,
            final ActorRef sender);

    /**
     * Convenience method: retrieve the things-shard-region from the entity region map.
     *
     * @return the things shard region if it exists in the entity region map, deadletters otherwise.
     */
    default ActorRef thingsShardRegion() {
        return entityRegionMap().lookup(ThingCommand.RESOURCE_TYPE).orElse(context().system().deadLetters());
    }

    /**
     * Convenience method: forward a message to things-shard-region.
     * Do not call {@code Actor.forward(Object, ActorContext)} because it is not thread-safe.
     *
     * @param message message to forward.
     * @param sender sender of the command.
     * @return true.
     */
    default boolean forwardToThingsShardRegion(final Object message, final ActorRef sender) {
        thingsShardRegion().tell(message, sender);
        return true;
    }

    /**
     * Convenience method: reply a message to sender.
     *
     * @param message message to forward.
     * @param sender whom to reply to.
     * @return true.
     */
    default boolean replyToSender(final Object message, final ActorRef sender) {
        sender.tell(message, self());
        return true;
    }
}
