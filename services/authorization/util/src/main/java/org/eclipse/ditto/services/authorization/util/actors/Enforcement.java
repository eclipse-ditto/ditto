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
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Do NOT call the methods outside this package.
 */
interface Enforcement extends Actor {

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
     * @return true.
     */
    boolean enforceCreateThingForNonexistentThingWithPolicyId(final CreateThing createThing,
            final String policyId,
            final ActorRef sender);

    /**
     * Method shared by {@code PolicyCommandEnforcement} with other mixins: authorize a {@code PolicyCommand} and add
     * read subjects to it.
     * @param command the command to authorize.
     * @param enforcer the enforcer to carry out authorization.
     * @param <T> type of the command.
     * @return command with read subjects added if it is authorized, an empty optional otherwise.
     */
    <T extends PolicyCommand> Optional<T> authorizePolicyCommand(final PolicyCommand<T> command,
            final Enforcer enforcer);

    /**
     * Convenience method: retrieve the things-shard-region from the entity region map.
     *
     * @return the things shard region if it exists in the entity region map, deadletters otherwise.
     */
    default ActorRef thingsShardRegion() {
        return shardRegionForResourceType(ThingCommand.RESOURCE_TYPE);
    }

    /**
     * Convenience method: retrieve the policies-shard-region from the entity region map.
     *
     * @return the policies shard region if it exists in the entity region map, deadletters otherwise.
     */
    default ActorRef policiesShardRegion() {
        return shardRegionForResourceType(PolicyCommand.RESOURCE_TYPE);
    }

    /**
     * Convenience method: retrieve shard region for a resource type.
     * @param resourceType the resource type.
     * @return the shard region.
     */
    default ActorRef shardRegionForResourceType(final String resourceType) {
        return entityRegionMap().lookup(resourceType).orElse(context().system().deadLetters());
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

    /**
     * Convenience method: report unexpected error or unknown response.
     */
    default void reportUnexpectedErrorOrResponse(final String hint,
            final ActorRef sender,
            final Object response,
            final Throwable error) {

        if (error != null) {
            reportUnexpectedError(hint, sender, error);
        } else {
            reportUnknownResponse(hint, sender, response);
        }
    }

    /**
     * Convenience method: report unknown error.
     */
    default void reportUnexpectedError(final String hint, final ActorRef sender, final Throwable error) {
        log().error(error, "Unexpected error {}", hint);

        sender.tell(GatewayInternalErrorException.newBuilder().build(), self());
    }

    /**
     * Convenience method: report unknown response.
     */
    default void reportUnknownResponse(final String hint, final ActorRef sender, final Object response) {
        log().error("Unexpected response {}: <{}>", hint, response);

        sender.tell(GatewayInternalErrorException.newBuilder().build(), self());
    }

    /**
     * Convenience method: extend a command by read-subjects header given by an enforcer.
     *
     * @param command the command to extend.
     * @param enforcer the enforcer.
     * @return the extended command.
     */
    static <T extends Command> T addReadSubjectsToCommand(final Command<T> command,
            final Enforcer enforcer) {

        return addReadSubjectsToCommand(command, getReadSubjects(command, enforcer));
    }

    /**
     * Convenience method: extend a command by read-subjects header given explicitly.
     *
     * @param <T> type of the thing command.
     * @param command the command to extend.
     * @param readSubjects explicitly-given read subjects.
     * @return the extended command.
     */
    static <T extends Command> T addReadSubjectsToCommand(final Command<T> command,
            final Set<String> readSubjects) {

        final DittoHeaders newHeaders = command.getDittoHeaders()
                .toBuilder()
                .readSubjects(readSubjects)
                .build();

        return command.setDittoHeaders(newHeaders);
    }

    /**
     * Convenience method: get read subjects from an enforcer.
     *
     * @param command the command to get read subjects for.
     * @param enforcer the enforcer.
     * @return read subjects of the command.
     */
    static Set<String> getReadSubjects(final Command<?> command, final Enforcer enforcer) {
        final ResourceKey resourceKey =
                ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, command.getResourcePath());
        return enforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ).getGranted();
    }
}
